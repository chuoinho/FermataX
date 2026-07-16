package me.aap.fermata.addon.tv.xtream;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.utils.function.CheckedConsumer;

/**
 * @author Andrey Pavlenko
 */
public class XtreamJsonStreamParser {

	public XtreamStatus parseStatus(InputStream in) throws IOException {
		try (JsonReader r = reader(in)) {
			boolean[] auth = {false};
			boolean[] seenAuth = {false};
			String[] status = new String[1];
			String[] message = new String[1];
			long[] expiryTime = new long[1];
			int[] activeConnections = {-1};
			int[] maxConnections = {-1};
			r.beginObject();

			while (r.hasNext()) {
				String name = r.nextName();
				if ("user_info".equals(name)) {
					r.beginObject();
					while (r.hasNext()) {
						switch (r.nextName()) {
							case "auth":
								auth[0] = nextBool(r);
								seenAuth[0] = true;
								break;
							case "status":
								status[0] = nextString(r);
								break;
							case "message":
								message[0] = nextString(r);
								break;
							case "exp_date":
								expiryTime[0] = nextLong(r, 0);
								break;
							case "active_cons":
								activeConnections[0] = nextInt(r, -1);
								break;
							case "max_connections":
								maxConnections[0] = nextInt(r, -1);
								break;
							default:
								r.skipValue();
						}
					}
					r.endObject();
				} else {
					switch (name) {
						case "message":
						case "error":
							message[0] = nextString(r);
							break;
						default:
							r.skipValue();
					}
				}
			}

			r.endObject();
			return new XtreamStatus(seenAuth[0] ? auth[0] : (status[0] != null), status[0],
					message[0], expiryTime[0], activeConnections[0], maxConnections[0]);
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public void parseLiveCategories(InputStream in,
																	CheckedConsumer<XtreamCategory, IOException> consumer)
			throws IOException {
		parseCategories(in, consumer);
	}

	public void parseVodCategories(InputStream in,
																 CheckedConsumer<XtreamCategory, IOException> consumer)
			throws IOException {
		parseCategories(in, consumer);
	}

	public void parseSeriesCategories(InputStream in,
																		CheckedConsumer<XtreamCategory, IOException> consumer)
			throws IOException {
		parseCategories(in, consumer);
	}

	private void parseCategories(InputStream in,
															 CheckedConsumer<XtreamCategory, IOException> consumer)
			throws IOException {
		try (JsonReader r = reader(in)) {
			r.beginArray();

			while (r.hasNext()) {
				String id = null;
				String name = null;
				String parentId = null;
				r.beginObject();

				while (r.hasNext()) {
					switch (r.nextName()) {
						case "category_id":
							id = nextString(r);
							break;
						case "category_name":
							name = nextString(r);
							break;
						case "parent_id":
							parentId = nextString(r);
							break;
						default:
							r.skipValue();
					}
				}

				r.endObject();
				if ((id != null) && !id.isEmpty()) consumer.accept(new XtreamCategory(id, name, parentId));
			}

			r.endArray();
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public void parseLiveStreams(InputStream in,
															 CheckedConsumer<XtreamChannel, IOException> consumer)
			throws IOException {
		try (JsonReader r = reader(in)) {
			r.beginArray();

			while (r.hasNext()) {
				XtreamChannel channel = parseLiveStream(r);
				if (channel != null) consumer.accept(channel);
			}

			r.endArray();
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public XtreamChannel parseFirstLiveStream(InputStream in) throws IOException {
		try (JsonReader r = reader(in)) {
			r.beginArray();

			while (r.hasNext()) {
				XtreamChannel channel = parseLiveStream(r);
				if (channel != null) return channel;
			}

			r.endArray();
			return null;
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	private XtreamChannel parseLiveStream(JsonReader r) throws IOException {
		int streamId = 0;
		String name = null;
		String icon = null;
		String epgChannelId = null;
		boolean tvArchive = false;
		int tvArchiveDuration = 0;
		String categoryId = null;
		r.beginObject();

		while (r.hasNext()) {
			switch (r.nextName()) {
				case "stream_id":
					streamId = nextInt(r, 0);
					break;
				case "name":
					name = nextString(r);
					break;
				case "stream_icon":
					icon = nextString(r);
					break;
				case "epg_channel_id":
					epgChannelId = nextString(r);
					break;
				case "tv_archive":
					tvArchive = nextBool(r);
					break;
				case "tv_archive_duration":
					tvArchiveDuration = nextInt(r, 0);
					break;
				case "category_id":
					categoryId = nextString(r);
					break;
				default:
					r.skipValue();
			}
		}

		r.endObject();
		return (streamId > 0) ? new XtreamChannel(streamId, name, icon, epgChannelId, tvArchive,
				tvArchiveDuration, categoryId) : null;
	}

	public void parseVodStreams(InputStream in,
															CheckedConsumer<XtreamMovie, IOException> consumer)
			throws IOException {
		try (JsonReader r = reader(in)) {
			r.beginArray();

			while (r.hasNext()) {
				int streamId = 0;
				String name = null;
				String icon = null;
				String categoryId = null;
				String containerExtension = null;
				r.beginObject();

				while (r.hasNext()) {
					switch (r.nextName()) {
						case "stream_id":
							streamId = nextInt(r, 0);
							break;
						case "name":
							name = nextString(r);
							break;
						case "stream_icon":
							icon = nextString(r);
							break;
						case "category_id":
							categoryId = nextString(r);
							break;
						case "container_extension":
							containerExtension = nextString(r);
							break;
						default:
							r.skipValue();
					}
				}

				r.endObject();
				if (streamId > 0) {
					consumer.accept(new XtreamMovie(streamId, name, icon, categoryId,
							containerExtension));
				}
			}

			r.endArray();
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public void parseSeries(InputStream in,
													CheckedConsumer<XtreamSeries, IOException> consumer)
			throws IOException {
		try (JsonReader r = reader(in)) {
			r.beginArray();

			while (r.hasNext()) {
				int seriesId = 0;
				String name = null;
				String icon = null;
				String categoryId = null;
				r.beginObject();

				while (r.hasNext()) {
					switch (r.nextName()) {
						case "series_id":
							seriesId = nextInt(r, 0);
							break;
						case "name":
							name = nextString(r);
							break;
						case "cover":
						case "cover_big":
						case "stream_icon":
							if (icon == null) icon = nextString(r);
							else r.skipValue();
							break;
						case "category_id":
							categoryId = nextString(r);
							break;
						default:
							r.skipValue();
					}
				}

				r.endObject();
				if (seriesId > 0) consumer.accept(new XtreamSeries(seriesId, name, icon, categoryId));
			}

			r.endArray();
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public List<XtreamSeason> parseSeriesInfo(InputStream in) throws IOException {
		try (JsonReader r = reader(in)) {
			Map<Integer, SeasonBuilder> seasons = new LinkedHashMap<>();
			r.beginObject();

			while (r.hasNext()) {
				switch (r.nextName()) {
					case "seasons":
						parseSeasons(r, seasons);
						break;
					case "episodes":
						parseEpisodesBySeason(r, seasons);
						break;
					default:
						r.skipValue();
				}
			}

			r.endObject();
			List<XtreamSeason> result = new ArrayList<>(seasons.size());
			for (SeasonBuilder season : seasons.values()) result.add(season.build());
			return result;
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	public List<XtreamEpgProgram> parseEpg(InputStream in) throws IOException {
		try (JsonReader r = reader(in)) {
			List<XtreamEpgProgram> epg = new ArrayList<>();
			JsonToken token = r.peek();

			if (token == JsonToken.BEGIN_ARRAY) {
				parseEpgArray(r, epg);
			} else if (token == JsonToken.BEGIN_OBJECT) {
				r.beginObject();
				while (r.hasNext()) {
					String name = r.nextName();
					if ("epg_listings".equals(name) || "listings".equals(name)) {
						parseEpgArray(r, epg);
					} else {
						r.skipValue();
					}
				}
				r.endObject();
			} else {
				r.skipValue();
			}

			return epg;
		} catch (IllegalStateException ex) {
			throw invalid(ex);
		}
	}

	private void parseEpgArray(JsonReader r, List<XtreamEpgProgram> epg) throws IOException {
		if (r.peek() != JsonToken.BEGIN_ARRAY) {
			r.skipValue();
			return;
		}

		r.beginArray();
		while (r.hasNext()) {
			XtreamEpgProgram program = parseEpgProgram(r);
			if ((program != null) && program.isValid()) epg.add(program);
		}
		r.endArray();
	}

	private XtreamEpgProgram parseEpgProgram(JsonReader r) throws IOException {
		if (r.peek() != JsonToken.BEGIN_OBJECT) {
			r.skipValue();
			return null;
		}

		String title = null;
		String description = null;
		String icon = null;
		String start = null;
		String stop = null;
		String startTimestamp = null;
		String stopTimestamp = null;
		boolean archive = false;
		r.beginObject();

		while (r.hasNext()) {
			switch (r.nextName()) {
				case "title":
					title = XtreamEpgProgram.decodeText(nextString(r));
					break;
				case "description":
				case "desc":
					description = XtreamEpgProgram.decodeText(nextString(r));
					break;
				case "icon":
				case "cover":
					if (icon == null) icon = nextString(r);
					else r.skipValue();
					break;
				case "start":
					start = nextString(r);
					break;
				case "end":
				case "stop":
					stop = nextString(r);
					break;
				case "start_timestamp":
					startTimestamp = nextString(r);
					break;
				case "stop_timestamp":
				case "end_timestamp":
					stopTimestamp = nextString(r);
					break;
				case "has_archive":
				case "archive":
					archive = nextBool(r);
					break;
				default:
					r.skipValue();
			}
		}

		r.endObject();
		long startTime = XtreamEpgProgram.parseTime((startTimestamp != null) ? startTimestamp : start);
		long stopTime = XtreamEpgProgram.parseTime((stopTimestamp != null) ? stopTimestamp : stop);
		return new XtreamEpgProgram(startTime, stopTime, title, description, icon, archive);
	}

	private void parseSeasons(JsonReader r, Map<Integer, SeasonBuilder> seasons)
			throws IOException {
		JsonToken token = r.peek();
		if (token == JsonToken.NULL) {
			r.nextNull();
			return;
		}
		if (token != JsonToken.BEGIN_ARRAY) {
			r.skipValue();
			return;
		}

		r.beginArray();

		while (r.hasNext()) {
			int seasonNumber = 0;
			String name = null;
			String icon = null;
			r.beginObject();

			while (r.hasNext()) {
				switch (r.nextName()) {
					case "season_number":
						seasonNumber = nextInt(r, 0);
						break;
					case "name":
						name = nextString(r);
						break;
					case "cover":
					case "cover_big":
					case "poster":
						if (icon == null) icon = nextString(r);
						else r.skipValue();
						break;
					default:
						r.skipValue();
				}
			}

			r.endObject();
			if (seasonNumber > 0) getSeason(seasons, seasonNumber).set(name, icon);
		}

		r.endArray();
	}

	private void parseEpisodesBySeason(JsonReader r, Map<Integer, SeasonBuilder> seasons)
			throws IOException {
		JsonToken token = r.peek();
		if (token == JsonToken.NULL) {
			r.nextNull();
			return;
		}

		if (token == JsonToken.BEGIN_OBJECT) {
			r.beginObject();
			while (r.hasNext()) {
				int seasonNumber = parseInt(r.nextName(), 0);
				parseEpisodeArray(r, seasons, seasonNumber);
			}
			r.endObject();
		} else if (token == JsonToken.BEGIN_ARRAY) {
			parseEpisodeArray(r, seasons, 0);
		} else {
			r.skipValue();
		}
	}

	private void parseEpisodeArray(JsonReader r, Map<Integer, SeasonBuilder> seasons,
																 int defaultSeasonNumber) throws IOException {
		JsonToken token = r.peek();
		if (token == JsonToken.NULL) {
			r.nextNull();
			return;
		}
		if (token != JsonToken.BEGIN_ARRAY) {
			r.skipValue();
			return;
		}

		r.beginArray();

		while (r.hasNext()) {
			XtreamEpisode episode = parseEpisode(r, defaultSeasonNumber);
			if (episode == null) continue;

			int seasonNumber = episode.getSeasonNumber();
			if (seasonNumber <= 0) seasonNumber = defaultSeasonNumber;
			if (seasonNumber <= 0) seasonNumber = 1;
			getSeason(seasons, seasonNumber).episodes.add(episode);
		}

		r.endArray();
	}

	private XtreamEpisode parseEpisode(JsonReader r, int defaultSeasonNumber) throws IOException {
		if (r.peek() != JsonToken.BEGIN_OBJECT) {
			r.skipValue();
			return null;
		}

		int episodeId = 0;
		int seasonNumber = defaultSeasonNumber;
		int episodeNumber = 0;
		String name = null;
		String icon = null;
		String containerExtension = null;
		r.beginObject();

		while (r.hasNext()) {
			switch (r.nextName()) {
				case "id":
				case "episode_id":
					episodeId = nextInt(r, 0);
					break;
				case "season":
				case "season_number":
					seasonNumber = nextInt(r, seasonNumber);
					break;
				case "episode_num":
				case "episode_number":
					episodeNumber = nextInt(r, 0);
					break;
				case "title":
				case "name":
					name = nextString(r);
					break;
				case "container_extension":
					containerExtension = nextString(r);
					break;
				case "info":
					EpisodeInfo info = parseEpisodeInfo(r);
					if (name == null) name = info.name;
					if (icon == null) icon = info.icon;
					if (containerExtension == null) containerExtension = info.containerExtension;
					break;
				default:
					r.skipValue();
			}
		}

		r.endObject();
		return (episodeId > 0) ? new XtreamEpisode(episodeId, seasonNumber, episodeNumber,
				name, icon, containerExtension) : null;
	}

	private EpisodeInfo parseEpisodeInfo(JsonReader r) throws IOException {
		EpisodeInfo info = new EpisodeInfo();
		JsonToken token = r.peek();
		if (token == JsonToken.NULL) {
			r.nextNull();
			return info;
		}
		if (token != JsonToken.BEGIN_OBJECT) {
			r.skipValue();
			return info;
		}

		r.beginObject();

		while (r.hasNext()) {
			switch (r.nextName()) {
				case "title":
				case "name":
					if (info.name == null) info.name = nextString(r);
					else r.skipValue();
					break;
				case "movie_image":
				case "cover":
				case "cover_big":
				case "image":
					if (info.icon == null) info.icon = nextString(r);
					else r.skipValue();
					break;
				case "container_extension":
					info.containerExtension = nextString(r);
					break;
				default:
					r.skipValue();
			}
		}

		r.endObject();
		return info;
	}

	private SeasonBuilder getSeason(Map<Integer, SeasonBuilder> seasons, int seasonNumber) {
		SeasonBuilder season = seasons.get(seasonNumber);
		if (season == null) seasons.put(seasonNumber, season = new SeasonBuilder(seasonNumber));
		return season;
	}

	private static JsonReader reader(InputStream in) throws IOException {
		return new JsonReader(new InputStreamReader(prepareJsonInput(in), UTF_8));
	}

	static InputStream prepareJsonInput(InputStream in) throws IOException {
		PushbackInputStream p = new PushbackInputStream(in, 1);
		int c;

		do {
			c = p.read();
		} while ((c != -1) && Character.isWhitespace(c));

		if (c == -1) throw new IOException("Invalid Xtream response: empty response");
		if (c == '<') throw new IOException("Invalid Xtream response: expected JSON, got HTML");
		if ((c != '{') && (c != '[')) {
			throw new IOException("Invalid Xtream response: expected JSON, got '" + (char) c + "'");
		}

		p.unread(c);
		return p;
	}

	private static String nextString(JsonReader r) throws IOException {
		JsonToken token = r.peek();

		switch (token) {
			case NULL:
				r.nextNull();
				return null;
			case BOOLEAN:
				return String.valueOf(r.nextBoolean());
			default:
				return r.nextString();
		}
	}

	private static int nextInt(JsonReader r, int def) throws IOException {
		String value = nextString(r);
		if ((value == null) || value.isEmpty()) return def;

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			try {
				return (int) Double.parseDouble(value);
			} catch (NumberFormatException ignore) {
				return def;
			}
		}
	}

	private static long nextLong(JsonReader r, long def) throws IOException {
		String value = nextString(r);
		if ((value == null) || value.isEmpty() || "null".equalsIgnoreCase(value)) return def;

		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			try {
				return (long) Double.parseDouble(value);
			} catch (NumberFormatException ignore) {
				return def;
			}
		}
	}

	private static boolean nextBool(JsonReader r) throws IOException {
		JsonToken token = r.peek();
		if (token == JsonToken.BOOLEAN) return r.nextBoolean();
		if (token == JsonToken.NULL) {
			r.nextNull();
			return false;
		}

		String value = r.nextString();
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}

	private static int parseInt(String value, int def) {
		if ((value == null) || value.isEmpty()) return def;

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			try {
				return (int) Double.parseDouble(value);
			} catch (NumberFormatException ignore) {
				return def;
			}
		}
	}

	private static IOException invalid(Exception ex) {
		if (ex instanceof IOException) return (IOException) ex;
		return new IOException("Invalid Xtream response: expected JSON", ex);
	}

	private static final class SeasonBuilder {
		final int seasonNumber;
		final List<XtreamEpisode> episodes = new ArrayList<>();
		String name;
		String icon;

		SeasonBuilder(int seasonNumber) {
			this.seasonNumber = seasonNumber;
		}

		void set(String name, String icon) {
			if ((this.name == null) && (name != null) && !name.isEmpty()) this.name = name;
			if ((this.icon == null) && (icon != null) && !icon.isEmpty()) this.icon = icon;
		}

		XtreamSeason build() {
			return new XtreamSeason(seasonNumber, name, icon, episodes);
		}
	}

	private static final class EpisodeInfo {
		String name;
		String icon;
		String containerExtension;
	}
}
