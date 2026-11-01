package me.aap.fermata.ui.glass.road;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// shortcut: public Overpass suits a small user base only; needs a different source if usage grows.
public final class OverpassClient {

    public interface Opener {
        HttpURLConnection open(URL url) throws IOException;
    }

    public static final List<String> SERVERS = Collections.unmodifiableList(Arrays.asList(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
    ));

    private final List<String> servers;
    private final Opener opener;
    private final String userAgent;
    private volatile int lastSuccessServerIndex = 0;

    public OverpassClient(List<String> servers, Opener opener, String userAgent) {
        this.servers = servers != null && !servers.isEmpty() ? servers : SERVERS;
        this.opener = opener != null ? opener : url -> (HttpURLConnection) url.openConnection();
        this.userAgent = userAgent;
    }

    public static String query(TileId t) {
        double[] b = t.bbox();
        String bboxStr = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", b[0], b[1], b[2], b[3]);
        return "[out:json][timeout:25];(way[highway~\"^(motorway|trunk|primary|secondary|tertiary|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|unclassified|residential|living_street)$\"]("
                + bboxStr + ");node[highway=traffic_signals](" + bboxStr + "););out body geom;";
    }

    public String fetch(TileId t) throws IOException {
        String q = query(t);
        byte[] postBytes = ("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8);

        int nServers = servers.size();
        int startIdx = lastSuccessServerIndex;
        Exception lastError = null;

        for (int i = 0; i < nServers; i++) {
            int idx = (startIdx + i) % nServers;
            String serverUrl = servers.get(idx);
            HttpURLConnection conn = null;
            try {
                URL u = new URL(serverUrl);
                conn = opener.open(u);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setFixedLengthStreamingMode(postBytes.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream is = conn.getInputStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = is.read(buf)) != -1) {
                            baos.write(buf, 0, read);
                        }
                        lastSuccessServerIndex = idx;
                        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    }
                } else {
                    lastError = new IOException("Server " + serverUrl + " returned HTTP " + code);
                }
            } catch (Exception e) {
                lastError = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        throw new IOException("All Overpass servers failed", lastError);
    }

    public static long backoffMs(int failures, Random r) {
        int f = Math.max(0, failures);
        long base = (f >= 7) ? 3_600_000L : Math.min(30_000L * (1L << f), 3_600_000L);
        double jitterFraction = r != null ? r.nextDouble() * 0.20 : 0.0;
        return base + (long) (base * jitterFraction);
    }
}
