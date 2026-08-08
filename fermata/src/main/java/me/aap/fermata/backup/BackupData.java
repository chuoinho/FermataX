package me.aap.fermata.backup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Complete logical, portable configuration before file-level encryption. */
public final class BackupData {
	private final Map<String, Map<String, Object>> preferences;
	private final Map<String, Map<String, String>> securePreferences;
	private final Map<String, Section> sections;

	public BackupData(Map<String, Map<String, Object>> preferences,
			Map<String, Map<String, String>> securePreferences,
			Map<String, Section> sections) {
		this.preferences = copyObjectStores(preferences);
		this.securePreferences = copyStringStores(securePreferences);
		this.sections = copySections(sections);
	}

	public Map<String, Map<String, Object>> preferences() {
		return preferences;
	}

	public Map<String, Map<String, String>> securePreferences() {
		return securePreferences;
	}

	public Map<String, Section> sections() {
		return sections;
	}

	public BackupData withSections(Map<String, Section> replacement) {
		return new BackupData(preferences, securePreferences, replacement);
	}

	private static Map<String, Map<String, Object>> copyObjectStores(
			Map<String, Map<String, Object>> source) {
		Map<String, Map<String, Object>> result = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Object>> entry :
				Objects.requireNonNull(source, "preferences").entrySet()) {
			result.put(requireName(entry.getKey()), Map.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private static Map<String, Map<String, String>> copyStringStores(
			Map<String, Map<String, String>> source) {
		Map<String, Map<String, String>> result = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry :
				Objects.requireNonNull(source, "securePreferences").entrySet()) {
			result.put(requireName(entry.getKey()), Map.copyOf(entry.getValue()));
		}
		return Map.copyOf(result);
	}

	private static Map<String, Section> copySections(Map<String, Section> source) {
		Map<String, Section> result = new LinkedHashMap<>();
		for (Map.Entry<String, Section> entry :
				Objects.requireNonNull(source, "sections").entrySet()) {
			String id = requireName(entry.getKey());
			Section section = Objects.requireNonNull(entry.getValue(), "section");
			if (!id.equals(section.id())) throw new IllegalArgumentException("Section ID mismatch");
			result.put(id, section);
		}
		return Map.copyOf(result);
	}

	private static String requireName(String value) {
		if ((value == null) || value.isBlank()) throw new IllegalArgumentException("Blank backup name");
		return value;
	}

	public record Section(String id, int version, byte[] data) {
		public Section {
			id = requireName(id);
			if (version <= 0) throw new IllegalArgumentException("Invalid section version");
			data = Objects.requireNonNull(data, "data").clone();
		}

		@Override
		public byte[] data() {
			return data.clone();
		}
	}
}
