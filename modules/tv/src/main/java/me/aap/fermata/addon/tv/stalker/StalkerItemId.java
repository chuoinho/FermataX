package me.aap.fermata.addon.tv.stalker;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLDecoder;
import java.net.URLEncoder;

final class StalkerItemId {
	private StalkerItemId() {
	}

	static String category(String scheme, int sourceId, String categoryId, String name) {
		return scheme + ':' + sourceId + ':' + encode(categoryId) + ':' + encode(name);
	}

	static Category parseCategory(String id, String scheme) {
		String[] parts = split(id, scheme, 3);
		return new Category(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]));
	}

	static String channel(String scheme, int sourceId, String categoryId, String categoryName,
			String channelId) {
		return category(scheme, sourceId, categoryId, categoryName) + ':' + encode(channelId);
	}

	static Channel parseChannel(String id, String scheme) {
		String[] parts = split(id, scheme, 4);
		return new Channel(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]),
				decode(parts[3]));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, UTF_8).replace("+", "%20");
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, UTF_8);
	}

	private static String[] split(String id, String scheme, int expected) {
		String prefix = scheme + ':';
		if (!id.startsWith(prefix)) throw new IllegalArgumentException("Invalid Stalker item ID");
		String[] parts = id.substring(prefix.length()).split(":", -1);
		if (parts.length != expected) throw new IllegalArgumentException("Invalid Stalker item ID");
		return parts;
	}

	record Category(int sourceId, String categoryId, String categoryName) {
	}

	record Channel(int sourceId, String categoryId, String categoryName, String channelId) {
	}
}
