package me.aap.fermata.addon.podcast.provider;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.utils.async.FutureSupplier;

public final class FyydSearchProvider implements PodcastSearchProvider {
	private static final String ENDPOINT = "https://api.fyyd.de/0.2/search/podcast";
	private final PodcastHttpClient http;

	public FyydSearchProvider(PodcastHttpClient http) {
		this.http = http;
	}

	@Override
	public String getId() {
		return "fyyd";
	}

	@Override
	public FutureSupplier<List<PodcastSearchResult>> search(PodcastSearchRequest request) {
		int count = Math.min(request.getLimit(), 20);
		String url = ENDPOINT + "?title=" + URLEncoder.encode(request.getQuery(), UTF_8)
				.replace("+", "%20") + "&count=" + count + "&page=0";
		return http.getJson(url, input -> parse(input, count));
	}

	static List<PodcastSearchResult> parse(InputStream input, int limit) throws IOException {
		List<PodcastSearchResult> results = new ArrayList<>(limit);
		int status = 0;
		String message = "";
		try (JsonReader reader = new JsonReader(new InputStreamReader(input, UTF_8))) {
			reader.beginObject();
			while (reader.hasNext()) {
				switch (reader.nextName()) {
					case "status" -> status = PodcastJson.integer(reader);
					case "msg" -> message = PodcastJson.string(reader);
					case "data" -> readResults(reader, results, limit);
					default -> reader.skipValue();
				}
			}
			reader.endObject();
		} catch (IllegalStateException error) {
			throw new IOException("Invalid Fyyd podcast search response", error);
		}
		if (status != 1) {
			throw new IOException(message.isEmpty() ? "Fyyd podcast search failed" : message);
		}
		return results;
	}

	private static void readResults(JsonReader reader, List<PodcastSearchResult> results, int limit)
			throws IOException {
		reader.beginArray();
		while (reader.hasNext()) {
			PodcastSearchResult result = readResult(reader);
			if ((result != null) && (results.size() < limit)) results.add(result);
		}
		reader.endArray();
	}

	private static PodcastSearchResult readResult(JsonReader reader) throws IOException {
		String id = "";
		String title = "";
		String author = "";
		String description = "";
		String subtitle = "";
		String feed = "";
		String artwork = "";
		String fallbackArtwork = "";
		String website = "";
		String language = "";
		int episodeCount = 0;

		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "id" -> id = PodcastJson.string(reader);
				case "title" -> title = PodcastJson.string(reader);
				case "author" -> author = PodcastJson.string(reader);
				case "description" -> description = PodcastJson.string(reader);
				case "subtitle" -> subtitle = PodcastJson.string(reader);
				case "xmlURL" -> feed = PodcastJson.string(reader);
				case "layoutImageURL", "imgURL" -> {
					String value = PodcastJson.string(reader);
					if (artwork.isEmpty()) artwork = value;
				}
				case "smallImageURL", "thumbImageURL" -> {
					String value = PodcastJson.string(reader);
					if (fallbackArtwork.isEmpty()) fallbackArtwork = value;
				}
				case "htmlURL" -> website = PodcastJson.string(reader);
				case "language" -> language = PodcastJson.string(reader);
				case "episode_count" -> episodeCount = PodcastJson.integer(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		return PodcastSearchResult.create("fyyd", id, title, author,
				description.isEmpty() ? subtitle : description, feed,
				artwork.isEmpty() ? fallbackArtwork : artwork, website, language,
				episodeCount, false);
	}
}
