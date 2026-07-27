package me.aap.fermata.addon.stremio.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface TransportResponse extends AutoCloseable {
	int status();

	Map<String, String> headers();

	InputStream body() throws IOException;

	@Override
	void close();
}
