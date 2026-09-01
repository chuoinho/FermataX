package me.aap.fermata.addon.tv.stalker;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record StalkerPlaybackLink(URI uri, Map<String, String> headers) {
	StalkerPlaybackLink {
		headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
	}

	@Override
	public String toString() {
		return "StalkerPlaybackLink{" + uri.getScheme() + "://" + uri.getHost() +
				", headers=" + headers.size() + '}';
	}
}
