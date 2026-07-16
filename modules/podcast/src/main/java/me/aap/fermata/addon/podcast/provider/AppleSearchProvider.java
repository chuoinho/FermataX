package me.aap.fermata.addon.podcast.provider;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.utils.async.FutureSupplier;

public final class AppleSearchProvider implements PodcastSearchProvider {
	private static final String ENDPOINT = "https://itunes.apple.com/search";
	private final PodcastHttpClient http;

	public AppleSearchProvider(PodcastHttpClient http) {
		this.http = http;
	}

	@Override
	public String getId() {
		return "apple";
	}

	@Override
	public FutureSupplier<List<PodcastSearchResult>> search(PodcastSearchRequest request) {
		StringBuilder url = new StringBuilder(ENDPOINT)
				.append("?term=").append(encode(request.getQuery()))
				.append("&media=podcast&entity=podcast&explicit=Yes")
				.append("&limit=").append(request.getLimit());
		if (!request.getCountry().isEmpty()) url.append("&country=").append(request.getCountry());
		if (!request.getLanguage().isEmpty()) url.append("&lang=").append(request.getLanguage());
		return http.getJson(url.toString(), input -> parse(input, request.getLimit()));
	}

	static List<PodcastSearchResult> parse(InputStream input, int limit) throws IOException {
		List<PodcastSearchResult> results = new ArrayList<>(limit);
		try (JsonReader reader = new JsonReader(new InputStreamReader(input, UTF_8))) {
			reader.beginObject();
			while (reader.hasNext()) {
				if ("results".equals(reader.nextName())) readResults(reader, results, limit);
				else reader.skipValue();
			}
			reader.endObject();
		} catch (IllegalStateException error) {
			throw new IOException("Invalid Apple podcast search response", error);
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
		String kind = "";
		String id = "";
		String title = "";
		String trackTitle = "";
		String author = "";
		String feed = "";
		String artwork600 = "";
		String artwork100 = "";
		String website = "";
		String explicit = "";
		int episodeCount = 0;

		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "kind" -> kind = PodcastJson.string(reader);
				case "collectionId", "trackId" -> {
					String value = PodcastJson.string(reader);
					if (id.isEmpty()) id = value;
				}
				case "collectionName" -> title = PodcastJson.string(reader);
				case "trackName" -> trackTitle = PodcastJson.string(reader);
				case "artistName" -> author = PodcastJson.string(reader);
				case "feedUrl" -> feed = PodcastJson.string(reader);
				case "artworkUrl600" -> artwork600 = PodcastJson.string(reader);
				case "artworkUrl100" -> artwork100 = PodcastJson.string(reader);
				case "collectionViewUrl" -> website = PodcastJson.string(reader);
				case "collectionExplicitness", "contentAdvisoryRating" ->
						explicit = PodcastJson.string(reader);
				case "trackCount" -> episodeCount = PodcastJson.integer(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		if (!"podcast".equalsIgnoreCase(kind)) return null;
		return PodcastSearchResult.create("apple", id, title.isEmpty() ? trackTitle : title,
				author, "", feed, artwork600.isEmpty() ? artwork100 : artwork600, website, "",
				episodeCount, "explicit".equalsIgnoreCase(explicit));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, UTF_8).replace("+", "%20");
	}
}
