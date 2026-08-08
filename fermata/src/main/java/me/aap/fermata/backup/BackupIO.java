package me.aap.fermata.backup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Bounded binary helpers shared by base and addon-owned backup sections. */
public final class BackupIO {
	public static final int MAX_STRING_BYTES = 8 * 1024 * 1024;
	public static final int MAX_SECTION_BYTES = 16 * 1024 * 1024;

	private BackupIO() {
	}

	public static void writeString(DataOutput output, String value) throws IOException {
		byte[] bytes = value.getBytes(UTF_8);
		if (bytes.length > MAX_STRING_BYTES) throw new IOException("Backup string is too large");
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	public static String readString(DataInput input) throws IOException {
		int length = readLength(input, MAX_STRING_BYTES, "string");
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		return new String(bytes, UTF_8);
	}

	public static void writeNullableString(DataOutput output, String value) throws IOException {
		output.writeBoolean(value != null);
		if (value != null) writeString(output, value);
	}

	public static String readNullableString(DataInput input) throws IOException {
		return input.readBoolean() ? readString(input) : null;
	}

	public static void writeBytes(DataOutput output, byte[] value) throws IOException {
		if (value.length > MAX_SECTION_BYTES) throw new IOException("Backup section is too large");
		output.writeInt(value.length);
		output.write(value);
	}

	public static byte[] readBytes(DataInput input) throws IOException {
		int length = readLength(input, MAX_SECTION_BYTES, "section");
		byte[] value = new byte[length];
		input.readFully(value);
		return value;
	}

	public static int readCount(DataInput input, int maximum, String description)
			throws IOException {
		return readLength(input, maximum, description);
	}

	private static int readLength(DataInput input, int maximum, String description)
			throws IOException {
		int value = input.readInt();
		if ((value < 0) || (value > maximum)) {
			throw new IOException("Invalid backup " + description + " length");
		}
		return value;
	}
}
