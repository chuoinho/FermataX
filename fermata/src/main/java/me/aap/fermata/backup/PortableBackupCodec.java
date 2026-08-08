package me.aap.fermata.backup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned, password-encrypted FermataX backup file codec. */
public final class PortableBackupCodec {
	public static final String EXTENSION = ".fxbackup";
	private static final byte[] MAGIC = "FermataXBackup\0".getBytes(UTF_8);
	private static final int FORMAT_VERSION = 1;
	private static final String KDF = "PBKDF2WithHmacSHA256";
	private static final int KDF_ITERATIONS = 310_000;
	private static final int SALT_LENGTH = 16;
	private static final int IV_LENGTH = 12;
	private static final int KEY_BITS = 256;
	private static final int TAG_BITS = 128;
	private static final int MAX_BACKUP_BYTES = 32 * 1024 * 1024;
	private final SecureRandom random;
	private final int iterations;
	private final BackupPayloadCodec payloadCodec = new BackupPayloadCodec();

	public PortableBackupCodec() {
		this(new SecureRandom(), KDF_ITERATIONS);
	}

	PortableBackupCodec(SecureRandom random, int iterations) {
		this.random = random;
		this.iterations = iterations;
	}

	public byte[] encode(BackupData data, char[] password, long createdTimestamp,
			int appVersionCode, String appVersionName) throws BackupException {
		validatePassword(password);
		byte[] payload = null;
		byte[] salt = new byte[SALT_LENGTH];
		byte[] iv = new byte[IV_LENGTH];
		byte[] key = null;
		try {
			payload = payloadCodec.encode(data);
			random.nextBytes(salt);
			random.nextBytes(iv);
			byte[] header = header(createdTimestamp, appVersionCode, appVersionName, iterations,
					salt, iv);
			key = deriveKey(password, salt, iterations);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
					new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(header);
			byte[] ciphertext = cipher.doFinal(payload);
			if ((header.length + 4L + ciphertext.length) > MAX_BACKUP_BYTES) {
				throw new BackupException(BackupException.Code.INVALID_FORMAT, "Backup is too large");
			}
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(header.length + 4 +
					ciphertext.length);
			bytes.write(header);
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeInt(ciphertext.length);
				output.write(ciphertext);
			}
			return bytes.toByteArray();
		} catch (BackupException ex) {
			throw ex;
		} catch (IOException | GeneralSecurityException ex) {
			throw new BackupException(BackupException.Code.INVALID_FORMAT,
					"Unable to create backup", ex);
		} finally {
			if (payload != null) Arrays.fill(payload, (byte) 0);
			if (key != null) Arrays.fill(key, (byte) 0);
			Arrays.fill(salt, (byte) 0);
			Arrays.fill(iv, (byte) 0);
		}
	}

	public Decoded decode(byte[] backup, char[] password) throws BackupException {
		validatePassword(password);
		if ((backup == null) || (backup.length > MAX_BACKUP_BYTES)) throw invalid("Invalid backup size");
		byte[] key = null;
		byte[] plaintext = null;
		try {
			ByteArrayInputStream bytes = new ByteArrayInputStream(backup);
			DataInputStream input = new DataInputStream(bytes);
			byte[] magic = new byte[MAGIC.length];
			input.readFully(magic);
			if (!Arrays.equals(magic, MAGIC)) throw invalid("Not a FermataX backup");
			int version = input.readInt();
			if (version != FORMAT_VERSION) throw new BackupException(
					BackupException.Code.UNSUPPORTED_VERSION, "Unsupported backup format version");
			long created = input.readLong();
			int appVersionCode = input.readInt();
			String appVersionName = BackupIO.readString(input);
			String kdf = BackupIO.readString(input);
			if (!KDF.equals(kdf)) throw invalid("Unsupported backup key derivation");
			int encodedIterations = input.readInt();
			if ((encodedIterations < 100_000) || (encodedIterations > 2_000_000)) {
				throw invalid("Invalid backup key derivation cost");
			}
			byte[] salt = readFixed(input, SALT_LENGTH);
			byte[] iv = readFixed(input, IV_LENGTH);
			int headerLength = backup.length - bytes.available();
			byte[] header = Arrays.copyOf(backup, headerLength);
			int ciphertextLength = BackupIO.readCount(input, MAX_BACKUP_BYTES, "ciphertext");
			if ((ciphertextLength != bytes.available()) || (ciphertextLength < 16)) {
				throw invalid("Invalid backup ciphertext length");
			}
			byte[] ciphertext = new byte[ciphertextLength];
			input.readFully(ciphertext);
			key = deriveKey(password, salt, encodedIterations);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
					new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(header);
			plaintext = cipher.doFinal(ciphertext);
			BackupData data = payloadCodec.decode(plaintext);
			return new Decoded(data, created, appVersionCode, appVersionName);
		} catch (AEADBadTagException ex) {
			throw new BackupException(BackupException.Code.AUTHENTICATION_FAILED,
					"Wrong password or corrupted backup");
		} catch (BackupException ex) {
			throw ex;
		} catch (EOFException ex) {
			throw invalid("Truncated backup", ex);
		} catch (IOException | GeneralSecurityException ex) {
			throw invalid("Unable to read backup", ex);
		} finally {
			if (key != null) Arrays.fill(key, (byte) 0);
			if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
		}
	}

	public static boolean hasMagic(byte[] data) {
		if ((data == null) || (data.length < MAGIC.length)) return false;
		for (int i = 0; i < MAGIC.length; i++) if (data[i] != MAGIC[i]) return false;
		return true;
	}

	private static byte[] header(long createdTimestamp, int appVersionCode, String appVersionName,
			int iterations, byte[] salt, byte[] iv) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.write(MAGIC);
			output.writeInt(FORMAT_VERSION);
			output.writeLong(createdTimestamp);
			output.writeInt(appVersionCode);
			BackupIO.writeString(output, appVersionName);
			BackupIO.writeString(output, KDF);
			output.writeInt(iterations);
			output.write(salt);
			output.write(iv);
		}
		return bytes.toByteArray();
	}

	private static byte[] deriveKey(char[] password, byte[] salt, int iterations)
			throws GeneralSecurityException {
		PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
		try {
			return SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
		} finally {
			spec.clearPassword();
		}
	}

	private static byte[] readFixed(DataInputStream input, int length) throws IOException {
		byte[] value = new byte[length];
		input.readFully(value);
		return value;
	}

	private static void validatePassword(char[] password) throws BackupException {
		if ((password == null) || (password.length < 8)) throw new BackupException(
				BackupException.Code.AUTHENTICATION_FAILED,
				"Backup password must contain at least 8 characters");
	}

	private static BackupException invalid(String message) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message);
	}

	private static BackupException invalid(String message, Throwable cause) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message, cause);
	}

	public record Decoded(BackupData data, long createdTimestamp, int appVersionCode,
			String appVersionName) {
	}
}
