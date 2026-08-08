package me.aap.fermata.backup;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.aap.fermata.security.SecurePreferenceStore;

/** Safe compatibility reader for legacy Fermata_prefs_*.zip files. */
public final class LegacyBackupReader {
	private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
	private static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;

	public Result read(InputStream source) throws BackupException {
		Map<String, Map<String, Object>> stores = new LinkedHashMap<>();
		int skippedSecure = 0;
		int total = 0;
		try (ZipInputStream zip = new ZipInputStream(source, UTF_8)) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (entry.isDirectory() || !entry.getName().endsWith(".xml")) continue;
				String fileName = entry.getName();
				if (fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
					throw invalid("Invalid legacy backup entry");
				}
				String name = fileName.substring(0, fileName.length() - 4);
				if (!name.matches("[A-Za-z0-9_.-]{1,160}")) {
					throw invalid("Invalid legacy preference name");
				}
				byte[] xml = readEntry(zip);
				total += xml.length;
				if (total > MAX_TOTAL_BYTES) throw invalid("Legacy backup is too large");
				if (SecurePreferenceStore.isKnownStoreName(name) ||
						AndroidBackupStateStore.containsEncryptedPreferenceKeyset(xml)) {
					skippedSecure++;
					continue;
				}
				if (stores.put(name, parsePreferences(xml)) != null) {
					throw invalid("Duplicate legacy preference store");
				}
			}
		} catch (BackupException ex) {
			throw ex;
		} catch (IOException ex) {
			throw invalid("Unable to read legacy backup", ex);
		}
		if (stores.isEmpty() && (skippedSecure == 0)) throw invalid("Legacy backup is empty");
		return new Result(new BackupData(stores, Map.of(), Map.of()), skippedSecure);
	}

	private static Map<String, Object> parsePreferences(byte[] xml) throws BackupException {
		Map<String, Object> values = new LinkedHashMap<>();
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new ByteArrayInputStream(xml), UTF_8.name());
			int event;
			do {
				event = parser.next();
			} while ((event != XmlPullParser.START_TAG) && (event != XmlPullParser.END_DOCUMENT));
			if ((event != XmlPullParser.START_TAG) || !"map".equals(parser.getName())) {
				throw invalid("Invalid legacy preference XML");
			}
			int rootDepth = parser.getDepth();
			while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if ((event == XmlPullParser.END_TAG) && (parser.getDepth() == rootDepth)) break;
				if (event != XmlPullParser.START_TAG) continue;
				String tag = parser.getName();
				String name = parser.getAttributeValue(null, "name");
				if ((name == null) || name.isEmpty()) throw invalid("Legacy preference key is missing");
				Object value = switch (tag) {
					case "string" -> parser.nextText();
					case "boolean" -> Boolean.parseBoolean(requiredAttribute(parser, "value"));
					case "float" -> Float.parseFloat(requiredAttribute(parser, "value"));
					case "int" -> Integer.parseInt(requiredAttribute(parser, "value"));
					case "long" -> Long.parseLong(requiredAttribute(parser, "value"));
					case "set" -> readSet(parser);
					default -> throw invalid("Unsupported legacy preference type");
				};
				if (values.put(name, value) != null) throw invalid("Duplicate legacy preference key");
			}
			return Map.copyOf(values);
		} catch (BackupException ex) {
			throw ex;
		} catch (XmlPullParserException | IOException | NumberFormatException ex) {
			throw invalid("Invalid legacy preference XML", ex);
		}
	}

	private static Set<String> readSet(XmlPullParser parser)
			throws IOException, XmlPullParserException, BackupException {
		int setDepth = parser.getDepth();
		LinkedHashSet<String> result = new LinkedHashSet<>();
		while (true) {
			int event = parser.next();
			if (event == XmlPullParser.END_DOCUMENT) throw invalid("Truncated legacy string set");
			if ((event == XmlPullParser.END_TAG) && (parser.getDepth() == setDepth)) break;
			if (event != XmlPullParser.START_TAG) continue;
			if (!"string".equals(parser.getName()) || !result.add(parser.nextText())) {
				throw invalid("Invalid legacy string set");
			}
		}
		return Set.copyOf(result);
	}

	private static String requiredAttribute(XmlPullParser parser, String name)
			throws BackupException {
		String value = parser.getAttributeValue(null, name);
		if (value == null) throw invalid("Legacy preference value is missing");
		return value;
	}

	private static byte[] readEntry(InputStream input) throws IOException, BackupException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int read; (read = input.read(buffer)) >= 0; ) {
			if ((bytes.size() + read) > MAX_ENTRY_BYTES) throw invalid("Legacy entry is too large");
			bytes.write(buffer, 0, read);
		}
		return bytes.toByteArray();
	}

	private static BackupException invalid(String message) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message);
	}

	private static BackupException invalid(String message, Throwable cause) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message, cause);
	}

	public record Result(BackupData data, int skippedSecureStores) {
	}
}
