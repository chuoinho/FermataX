package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class OverpassClientTest {

    private static class FakeHttpURLConnection extends HttpURLConnection {
        private final int responseCode;
        private final String responseBody;
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        FakeHttpURLConnection(URL url, int responseCode, String responseBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        @Override
        public void disconnect() {}

        @Override
        public boolean usingProxy() { return false; }

        @Override
        public void connect() {}

        @Override
        public int getResponseCode() { return responseCode; }

        @Override
        public OutputStream getOutputStream() { return outputStream; }

        @Override
        public InputStream getInputStream() throws IOException {
            if (responseCode >= 400) throw new IOException("HTTP " + responseCode);
            return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void queryHasBboxAndBothParts() {
        TileId t = new TileId(10, 20);
        String q = OverpassClient.query(t);
        assertTrue(q.contains("way[highway~"));
        assertTrue(q.contains("node[highway=traffic_signals]"));
        assertTrue(q.contains("out body geom;"));
        double[] b = t.bbox();
        assertTrue(q.contains(String.format(java.util.Locale.US, "%.6f", b[0])));
        assertTrue(q.contains(String.format(java.util.Locale.US, "%.6f", b[1])));
        assertTrue(q.contains(String.format(java.util.Locale.US, "%.6f", b[2])));
        assertTrue(q.contains(String.format(java.util.Locale.US, "%.6f", b[3])));
    }

    @Test
    public void fallsBackToNextServer() throws IOException {
        List<String> servers = Arrays.asList("http://server0.test", "http://server1.test");
        List<String> contactedUrls = new ArrayList<>();

        OverpassClient.Opener opener = url -> {
            contactedUrls.add(url.toString());
            if (url.toString().startsWith("http://server0.test")) {
                return new FakeHttpURLConnection(url, 502, "Bad Gateway");
            } else {
                return new FakeHttpURLConnection(url, 200, "{\"elements\":[]}");
            }
        };

        OverpassClient client = new OverpassClient(servers, opener, "TestUA");
        TileId t = new TileId(1, 1);

        String result = client.fetch(t);
        assertEquals("{\"elements\":[]}", result);
        assertEquals(2, contactedUrls.size());
        assertEquals("http://server0.test", contactedUrls.get(0));
        assertEquals("http://server1.test", contactedUrls.get(1));

        // Next fetch should start at server 1 directly
        contactedUrls.clear();
        String result2 = client.fetch(t);
        assertEquals("{\"elements\":[]}", result2);
        assertEquals(1, contactedUrls.size());
        assertEquals("http://server1.test", contactedUrls.get(0));
    }

    @Test
    public void allFailThrows() {
        List<String> servers = Arrays.asList("http://server0.test", "http://server1.test");
        OverpassClient.Opener opener = url -> new FakeHttpURLConnection(url, 500, "Error");
        OverpassClient client = new OverpassClient(servers, opener, "TestUA");

        try {
            client.fetch(new TileId(1, 1));
            fail("Expected IOException");
        } catch (IOException expected) {}
    }

    @Test
    public void backoffGrowsAndCaps() {
        Random r = new Random(42);
        long b0 = OverpassClient.backoffMs(0, r);
        assertTrue("b0 was " + b0, b0 >= 30_000L && b0 <= 36_000L);

        long b20 = OverpassClient.backoffMs(20, r);
        assertTrue("b20 was " + b20, b20 <= 3_600_000L * 1.2);
    }
}
