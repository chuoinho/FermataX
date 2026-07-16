package me.aap.fermata.addon.podcast.provider;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;

final class PodcastJson {
	private PodcastJson() {
	}

	static String string(JsonReader reader) throws IOException {
		JsonToken token = reader.peek();
		if (token == JsonToken.NULL) {
			reader.nextNull();
			return "";
		}
		if ((token == JsonToken.STRING) || (token == JsonToken.NUMBER)) {
			return reader.nextString().trim();
		}
		if (token == JsonToken.BOOLEAN) return Boolean.toString(reader.nextBoolean());
		reader.skipValue();
		return "";
	}

	static int integer(JsonReader reader) throws IOException {
		String value = string(reader);
		if (value.isEmpty()) return 0;
		try {
			return (int) Double.parseDouble(value);
		} catch (NumberFormatException ignore) {
			return 0;
		}
	}
}
