package me.aap.fermata.addon.stremio.net.http;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.net.ImmutableBytePayload;

public final class HttpResponseData {
	private final int status;
	private final URI finalUri;
	private final Map<String, String> headers;
	private final ImmutableBytePayload payload;

	public HttpResponseData(int status, URI finalUri, Map<String, String> headers, byte[] body) {
		this.status = status;
		this.finalUri = Objects.requireNonNull(finalUri, "finalUri");
		this.headers = Map.copyOf(headers);
		payload = ImmutableBytePayload.copyOf(body);
	}

	public int status() {
		return status;
	}

	public URI finalUri() {
		return finalUri;
	}

	public Map<String, String> headers() {
		return headers;
	}

	public byte[] body() {
		return payload.copy();
	}

	public ImmutableBytePayload payload() {
		return payload;
	}

	public String header(String name) {
		return headers.get(name.toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public String toString() {
		return "HttpResponseData[status=" + status + ", redacted, bodyBytes=" +
				payload.size() + ']';
	}
}
