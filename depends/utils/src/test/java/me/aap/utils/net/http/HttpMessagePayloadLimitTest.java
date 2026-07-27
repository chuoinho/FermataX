package me.aap.utils.net.http;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

public class HttpMessagePayloadLimitTest {
	@Test
	public void decodedPayloadCannotExceedCallerLimit() throws Exception {
		byte[] expanded = new byte[4096];
		byte[] compressed;
		try (var output = new ByteArrayOutputStream();
				var gzip = new GZIPOutputStream(output)) {
			gzip.write(expanded);
			gzip.finish();
			compressed = output.toByteArray();
		}

		assertThrows(IOException.class, () -> HttpMessageBase.decode(
				ByteBuffer.wrap(compressed), "gzip", 1024));
	}
}
