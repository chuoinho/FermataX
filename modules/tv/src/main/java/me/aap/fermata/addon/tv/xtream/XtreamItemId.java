package me.aap.fermata.addon.tv.xtream;

import android.net.Uri;

import me.aap.utils.text.SharedTextBuilder;

final class XtreamItemId {
	private XtreamItemId() {
	}

	static String category(String scheme, int sourceId, String categoryId, String name) {
		return categoryBuilder(scheme, sourceId, categoryId, name).releaseString();
	}

	static Category category(String id) {
		Parts p = new Parts(id);
		int sourceId = p.nextInt();
		String categoryId = p.nextDecoded();
		String name = p.lastDecoded();
		return new Category(sourceId, categoryId, name);
	}

	static String stream(String scheme, int sourceId, String categoryId, String categoryName,
										 int streamId) {
		return categoryBuilder(scheme, sourceId, categoryId, categoryName).append(':')
				.append(streamId).releaseString();
	}

	static Stream stream(String id) {
		Parts p = new Parts(id);
		int sourceId = p.nextInt();
		String categoryId = p.nextDecoded();
		String categoryName = p.nextDecoded();
		int streamId = p.lastInt();
		return new Stream(sourceId, categoryId, categoryName, streamId);
	}

	static String series(String scheme, int sourceId, String categoryId, String categoryName,
										 int seriesId, String seriesName) {
		return seriesBuilder(scheme, sourceId, categoryId, categoryName, seriesId, seriesName)
				.releaseString();
	}

	static Series series(String id) {
		Parts p = new Parts(id);
		int sourceId = p.nextInt();
		String categoryId = p.nextDecoded();
		String categoryName = p.nextDecoded();
		int seriesId = p.nextInt();
		String seriesName = p.lastDecoded();
		return new Series(sourceId, categoryId, categoryName, seriesId, seriesName);
	}

	static String season(String scheme, int sourceId, String categoryId, String categoryName,
										 int seriesId, String seriesName, int seasonNumber) {
		return seriesBuilder(scheme, sourceId, categoryId, categoryName, seriesId, seriesName)
				.append(':').append(seasonNumber).releaseString();
	}

	static Season season(String id) {
		Parts p = new Parts(id);
		int sourceId = p.nextInt();
		String categoryId = p.nextDecoded();
		String categoryName = p.nextDecoded();
		int seriesId = p.nextInt();
		String seriesName = p.nextDecoded();
		int seasonNumber = p.lastInt();
		return new Season(sourceId, categoryId, categoryName, seriesId, seriesName, seasonNumber);
	}

	static String episode(String scheme, int sourceId, String categoryId, String categoryName,
											 int seriesId, String seriesName, int seasonNumber, int episodeId) {
		return seriesBuilder(scheme, sourceId, categoryId, categoryName, seriesId, seriesName)
				.append(':').append(seasonNumber).append(':').append(episodeId).releaseString();
	}

	static Episode episode(String id) {
		Parts p = new Parts(id);
		int sourceId = p.nextInt();
		String categoryId = p.nextDecoded();
		String categoryName = p.nextDecoded();
		int seriesId = p.nextInt();
		String seriesName = p.nextDecoded();
		int seasonNumber = p.nextInt();
		int episodeId = p.lastInt();
		return new Episode(sourceId, categoryId, categoryName, seriesId, seriesName,
				seasonNumber, episodeId);
	}

	static String orig(String id, String scheme) {
		int idx = id.indexOf(scheme);
		return (idx < 0) ? id : id.substring(idx);
	}

	private static SharedTextBuilder categoryBuilder(String scheme, int sourceId,
																									 String categoryId, String categoryName) {
		return SharedTextBuilder.get().append(scheme).append(':').append(sourceId).append(':')
				.append(Uri.encode(categoryId)).append(':').append(Uri.encode(categoryName));
	}

	private static SharedTextBuilder seriesBuilder(String scheme, int sourceId, String categoryId,
																								 String categoryName, int seriesId,
																								 String seriesName) {
		return categoryBuilder(scheme, sourceId, categoryId, categoryName).append(':')
				.append(seriesId).append(':').append(Uri.encode(seriesName));
	}

	private static final class Parts {
		private final String id;
		private int start;

		Parts(String id) {
			this.id = id;
			start = id.indexOf(':') + 1;
		}

		String next() {
			int end = id.indexOf(':', start);
			String value = id.substring(start, end);
			start = end + 1;
			return value;
		}

		String last() {
			return id.substring(start);
		}

		String nextDecoded() {
			return Uri.decode(next());
		}

		String lastDecoded() {
			return Uri.decode(last());
		}

		int nextInt() {
			return Integer.parseInt(next());
		}

		int lastInt() {
			return Integer.parseInt(last());
		}
	}

	static class Category {
		final int sourceId;
		final String categoryId;
		final String categoryName;

		Category(int sourceId, String categoryId, String categoryName) {
			this.sourceId = sourceId;
			this.categoryId = categoryId;
			this.categoryName = categoryName;
		}
	}

	static final class Stream extends Category {
		final int streamId;

		Stream(int sourceId, String categoryId, String categoryName, int streamId) {
			super(sourceId, categoryId, categoryName);
			this.streamId = streamId;
		}
	}

	static class Series extends Category {
		final int seriesId;
		final String seriesName;

		Series(int sourceId, String categoryId, String categoryName, int seriesId,
					 String seriesName) {
			super(sourceId, categoryId, categoryName);
			this.seriesId = seriesId;
			this.seriesName = seriesName;
		}
	}

	static final class Season extends Series {
		final int seasonNumber;

		Season(int sourceId, String categoryId, String categoryName, int seriesId,
					 String seriesName, int seasonNumber) {
			super(sourceId, categoryId, categoryName, seriesId, seriesName);
			this.seasonNumber = seasonNumber;
		}
	}

	static final class Episode extends Series {
		final int seasonNumber;
		final int episodeId;

		Episode(int sourceId, String categoryId, String categoryName, int seriesId,
						String seriesName, int seasonNumber, int episodeId) {
			super(sourceId, categoryId, categoryName, seriesId, seriesName);
			this.seasonNumber = seasonNumber;
			this.episodeId = episodeId;
		}
	}
}
