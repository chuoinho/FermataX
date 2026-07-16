package me.aap.fermata.addon.radio;

import androidx.annotation.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

final class RadioSource {
	private final String id;
	private final String name;
	private final String url;

	private RadioSource(String id, String name, String url) {
		this.id = clean(id);
		this.name = clean(name);
		this.url = clean(url);
	}

	static RadioSource create(String name, String url) {
		return new RadioSource(UUID.randomUUID().toString(), name, url);
	}

	static RadioSource restore(String id, String name, String url) {
		return new RadioSource(id, name, url);
	}

	RadioSource update(String name, String url) {
		return new RadioSource(id, name, url);
	}

	boolean isValid() {
		if (id.isEmpty() || name.isEmpty() || url.isEmpty()) return false;
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null) return false;
			scheme = scheme.toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https")) && (uri.getHost() != null);
		} catch (URISyntaxException ex) {
			return false;
		}
	}

	@NonNull
	String getId() {
		return id;
	}

	@NonNull
	String getName() {
		return name;
	}

	@NonNull
	String getUrl() {
		return url;
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}
}
