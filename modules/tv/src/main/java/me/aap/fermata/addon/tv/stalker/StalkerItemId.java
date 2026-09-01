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

	static String section(String scheme, int sourceId, String type) {
		return scheme + ':' + sourceId + ':' + encode(type);
	}

	static Section parseSection(String id, String scheme) {
		String[] parts = split(id, scheme, 2);
		return new Section(Integer.parseInt(parts[0]), decode(parts[1]));
	}

	static String contentCategory(String scheme, int sourceId, String type, String categoryId,
			String name) {
		return section(scheme, sourceId, type) + ':' + encode(categoryId) + ':' + encode(name);
	}

	static ContentCategory parseContentCategory(String id, String scheme) {
		String[] parts = split(id, scheme, 4);
		return new ContentCategory(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]),
				decode(parts[3]));
	}

	static String content(String scheme, int sourceId, String type, String categoryId,
			String categoryName, String contentId) {
		return contentCategory(scheme, sourceId, type, categoryId, categoryName) + ':' +
				encode(contentId);
	}

	static Content parseContent(String id, String scheme) {
		String[] parts = split(id, scheme, 5);
		return new Content(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]),
				decode(parts[3]), decode(parts[4]));
	}

	static String season(String scheme, Content content, String seasonId) {
		return content(scheme, content.sourceId(), content.type(), content.categoryId(),
				content.categoryName(), content.contentId()) + ':' + encode(seasonId);
	}

	static Season parseSeason(String id, String scheme) {
		String[] parts = split(id, scheme, 6);
		return new Season(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]),
				decode(parts[3]), decode(parts[4]), decode(parts[5]));
	}

	static String episode(String scheme, Season season, String episodeId) {
		Content content = new Content(season.sourceId(), season.type(), season.categoryId(),
				season.categoryName(), season.contentId());
		return season(scheme, content, season.seasonId()) + ':' + encode(episodeId);
	}

	static Episode parseEpisode(String id, String scheme) {
		String[] parts = split(id, scheme, 7);
		return new Episode(Integer.parseInt(parts[0]), decode(parts[1]), decode(parts[2]),
				decode(parts[3]), decode(parts[4]), decode(parts[5]), decode(parts[6]));
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

	record Section(int sourceId, String type) {
	}

	record ContentCategory(int sourceId, String type, String categoryId, String categoryName) {
	}

	record Content(int sourceId, String type, String categoryId, String categoryName,
			String contentId) {
	}

	record Season(int sourceId, String type, String categoryId, String categoryName,
			String contentId, String seasonId) {
	}

	record Episode(int sourceId, String type, String categoryId, String categoryName,
			String contentId, String seasonId, String episodeId) {
	}
}
