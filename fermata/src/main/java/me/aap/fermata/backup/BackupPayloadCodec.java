package me.aap.fermata.backup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable typed serialization for the encrypted backup payload. */
final class BackupPayloadCodec {
	private static final int PAYLOAD_VERSION = 1;
	private static final int MAX_STORES = 1024;
	private static final int MAX_ENTRIES = 200_000;
	private static final int MAX_SET_VALUES = 100_000;
	private static final byte BOOLEAN = 1;
	private static final byte FLOAT = 2;
	private static final byte INTEGER = 3;
	private static final byte LONG = 4;
	private static final byte STRING = 5;
	private static final byte STRING_SET = 6;

	byte[] encode(BackupData data) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(PAYLOAD_VERSION);
			writeObjectStores(output, data.preferences());
			writeStringStores(output, data.securePreferences());
			writeSections(output, data.sections());
		}
		return bytes.toByteArray();
	}

	BackupData decode(byte[] encoded) throws IOException {
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
			int version = input.readInt();
			if (version != PAYLOAD_VERSION) throw new BackupException(
					BackupException.Code.UNSUPPORTED_VERSION, "Unsupported backup payload version");
			BackupData data = new BackupData(readObjectStores(input), readStringStores(input),
					readSections(input));
			if (input.read() != -1) throw invalid("Trailing backup payload data");
			return data;
		} catch (EOFException ex) {
			throw invalid("Truncated backup payload", ex);
		}
	}

	private static void writeObjectStores(DataOutputStream output,
			Map<String, Map<String, Object>> stores) throws IOException {
		writeSize(output, stores.size(), MAX_STORES, "preference stores");
		for (String store : sorted(stores.keySet())) {
			BackupIO.writeString(output, store);
			Map<String, Object> values = stores.get(store);
			writeSize(output, values.size(), MAX_ENTRIES, "preference entries");
			for (String key : sorted(values.keySet())) {
				BackupIO.writeString(output, key);
				writeValue(output, values.get(key));
			}
		}
	}

	private static Map<String, Map<String, Object>> readObjectStores(DataInputStream input)
			throws IOException {
		int count = BackupIO.readCount(input, MAX_STORES, "preference stores");
		Map<String, Map<String, Object>> stores = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			String name = BackupIO.readString(input);
			int entries = BackupIO.readCount(input, MAX_ENTRIES, "preference entries");
			Map<String, Object> values = new LinkedHashMap<>();
			for (int j = 0; j < entries; j++) {
				String key = BackupIO.readString(input);
				if (values.put(key, readValue(input)) != null) throw invalid("Duplicate preference key");
			}
			if (stores.put(name, values) != null) throw invalid("Duplicate preference store");
		}
		return stores;
	}

	private static void writeStringStores(DataOutputStream output,
			Map<String, Map<String, String>> stores) throws IOException {
		writeSize(output, stores.size(), MAX_STORES, "secure stores");
		for (String store : sorted(stores.keySet())) {
			BackupIO.writeString(output, store);
			Map<String, String> values = stores.get(store);
			writeSize(output, values.size(), MAX_ENTRIES, "secure entries");
			for (String key : sorted(values.keySet())) {
				BackupIO.writeString(output, key);
				BackupIO.writeString(output, values.get(key));
			}
		}
	}

	private static Map<String, Map<String, String>> readStringStores(DataInputStream input)
			throws IOException {
		int count = BackupIO.readCount(input, MAX_STORES, "secure stores");
		Map<String, Map<String, String>> stores = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			String name = BackupIO.readString(input);
			int entries = BackupIO.readCount(input, MAX_ENTRIES, "secure entries");
			Map<String, String> values = new LinkedHashMap<>();
			for (int j = 0; j < entries; j++) {
				String key = BackupIO.readString(input);
				if (values.put(key, BackupIO.readString(input)) != null) {
					throw invalid("Duplicate secure preference key");
				}
			}
			if (stores.put(name, values) != null) throw invalid("Duplicate secure store");
		}
		return stores;
	}

	private static void writeSections(DataOutputStream output,
			Map<String, BackupData.Section> sections) throws IOException {
		writeSize(output, sections.size(), MAX_STORES, "addon sections");
		for (String id : sorted(sections.keySet())) {
			BackupData.Section section = sections.get(id);
			BackupIO.writeString(output, id);
			output.writeInt(section.version());
			BackupIO.writeBytes(output, section.data());
		}
	}

	private static Map<String, BackupData.Section> readSections(DataInputStream input)
			throws IOException {
		int count = BackupIO.readCount(input, MAX_STORES, "addon sections");
		Map<String, BackupData.Section> sections = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			String id = BackupIO.readString(input);
			int version = input.readInt();
			BackupData.Section section;
			try {
				section = new BackupData.Section(id, version, BackupIO.readBytes(input));
			} catch (IllegalArgumentException ex) {
				throw invalid("Invalid addon section", ex);
			}
			if (sections.put(id, section) != null) throw invalid("Duplicate addon section");
		}
		return sections;
	}

	private static void writeValue(DataOutputStream output, Object value) throws IOException {
		if (value instanceof Boolean v) {
			output.writeByte(BOOLEAN);
			output.writeBoolean(v);
		} else if (value instanceof Float v) {
			output.writeByte(FLOAT);
			output.writeFloat(v);
		} else if (value instanceof Integer v) {
			output.writeByte(INTEGER);
			output.writeInt(v);
		} else if (value instanceof Long v) {
			output.writeByte(LONG);
			output.writeLong(v);
		} else if (value instanceof String v) {
			output.writeByte(STRING);
			BackupIO.writeString(output, v);
		} else if (value instanceof Set<?> values) {
			output.writeByte(STRING_SET);
			writeSize(output, values.size(), MAX_SET_VALUES, "string set");
			List<String> sorted = new ArrayList<>(values.size());
			for (Object entry : values) {
				if (!(entry instanceof String text)) throw new IOException("Unsupported set value");
				sorted.add(text);
			}
			sorted.sort(String::compareTo);
			for (String entry : sorted) BackupIO.writeString(output, entry);
		} else {
			throw new IOException("Unsupported preference value type");
		}
	}

	private static Object readValue(DataInputStream input) throws IOException {
		return switch (input.readUnsignedByte()) {
			case BOOLEAN -> input.readBoolean();
			case FLOAT -> input.readFloat();
			case INTEGER -> input.readInt();
			case LONG -> input.readLong();
			case STRING -> BackupIO.readString(input);
			case STRING_SET -> {
				int count = BackupIO.readCount(input, MAX_SET_VALUES, "string set");
				java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
				for (int i = 0; i < count; i++) {
					if (!values.add(BackupIO.readString(input))) throw invalid("Duplicate set value");
				}
				yield Set.copyOf(values);
			}
			default -> throw invalid("Unknown preference value type");
		};
	}

	private static List<String> sorted(Set<String> values) {
		return values.stream().sorted(Comparator.naturalOrder()).toList();
	}

	private static void writeSize(DataOutputStream output, int size, int maximum,
			String description) throws IOException {
		if ((size < 0) || (size > maximum)) throw new IOException("Too many " + description);
		output.writeInt(size);
	}

	private static BackupException invalid(String message) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message);
	}

	private static BackupException invalid(String message, Throwable cause) {
		return new BackupException(BackupException.Code.INVALID_FORMAT, message, cause);
	}
}
