package me.aap.fermata.addon.radio;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class RadioSourceStore {
	private static final String PREFS_NAME = "radio_sources";
	private static final String KEY_PREFIX = "source.";
	private static final String NAME_SUFFIX = ".name";
	private static final String URL_SUFFIX = ".url";
	private final SharedPreferences preferences;

	RadioSourceStore(Context context) {
		preferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
	}

	synchronized List<RadioSource> list() {
		Map<String, ?> values = preferences.getAll();
		List<RadioSource> sources = new ArrayList<>();

		for (Map.Entry<String, ?> entry : values.entrySet()) {
			String key = entry.getKey();
			if (!key.startsWith(KEY_PREFIX) || !key.endsWith(NAME_SUFFIX) ||
					!(entry.getValue() instanceof String name)) continue;

			String id = key.substring(KEY_PREFIX.length(), key.length() - NAME_SUFFIX.length());
			String url = preferences.getString(key(id, URL_SUFFIX), null);
			RadioSource source = RadioSource.restore(id, name, url);
			if (source.isValid()) sources.add(source);
		}

		sources.sort(Comparator.comparing(RadioSource::getName, String.CASE_INSENSITIVE_ORDER));
		return sources;
	}

	synchronized RadioSource find(String id) {
		String name = preferences.getString(key(id, NAME_SUFFIX), null);
		String url = preferences.getString(key(id, URL_SUFFIX), null);
		RadioSource source = RadioSource.restore(id, name, url);
		return source.isValid() ? source : null;
	}

	synchronized void save(RadioSource source) {
		if (!source.isValid()) throw new IllegalArgumentException("Invalid radio source");
		preferences.edit()
				.putString(key(source.getId(), NAME_SUFFIX), source.getName())
				.putString(key(source.getId(), URL_SUFFIX), source.getUrl())
				.apply();
	}

	synchronized void remove(RadioSource source) {
		preferences.edit()
				.remove(key(source.getId(), NAME_SUFFIX))
				.remove(key(source.getId(), URL_SUFFIX))
				.apply();
	}

	private static String key(String id, String suffix) {
		return KEY_PREFIX + id + suffix;
	}
}
