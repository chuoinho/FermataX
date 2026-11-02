package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@RunWith(RobolectricTestRunner.class)
public class RoadDataSourceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File storeDir;
    private SharedPreferences prefs;
    private RoadTileStore store;
    private FakeOverpassClient client;
    private long now;
    private LongSupplier clock;

    private static class FakeOverpassClient extends OverpassClient {
        int fetchCount = 0;
        String responseJson = "{\"elements\":[]}";
        boolean shouldThrow = false;
        CountDownLatch enterLatch = null;
        CountDownLatch blockLatch = null;

        FakeOverpassClient() {
            super(Collections.emptyList(), null, "TestAgent");
        }

        @Override
        public String fetch(TileId t) throws IOException {
            fetchCount++;
            if (enterLatch != null) {
                enterLatch.countDown();
            }
            if (blockLatch != null) {
                try {
                    blockLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            if (shouldThrow) {
                throw new IOException("Simulated network error");
            }
            return responseJson;
        }
    }

    @Before
    public void setUp() throws IOException {
        storeDir = tempFolder.newFolder("road_tiles");
        Context ctx = RuntimeEnvironment.getApplication();
        prefs = ctx.getSharedPreferences("test_road_tiles", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new RoadTileStore(storeDir, prefs);
        client = new FakeOverpassClient();
        now = 1000_000_000L;
        clock = () -> now;
    }

    @Test
    public void servesCachedTileWithoutNetwork() {
        double lat = 48.137;
        double lon = 11.575;
        TileId t = TileId.of(lat, lon);
        TileId ahead = t.ahead(0f);
        String validTileJson = "{\"elements\":[{\"type\":\"way\",\"id\":1,\"nodes\":[10,11],\"geometry\":[{\"lat\":48.137,\"lon\":11.575},{\"lat\":48.138,\"lon\":11.576}]}]}";
        assertTrue(store.put(t, validTileJson, now));
        assertTrue(store.put(ahead, validTileJson, now));

        Executor directExecutor = Runnable::run;
        RoadDataSource source = new RoadDataSource(store, client, directExecutor, clock);

        List<RoadTile> tiles = source.tilesAround(lat, lon, 0f, true);
        assertNotNull(tiles);
        assertFalse(tiles.isEmpty());
        assertEquals(0, client.fetchCount);
    }

    @Test
    public void fetchesMissingCurrentTileWhenLiveAllowed() {
        double lat = 48.137;
        double lon = 11.575;
        TileId t = TileId.of(lat, lon);
        TileId ahead = t.ahead(0f);
        String dummyJson = "{\"elements\":[]}";
        assertTrue(store.put(ahead, dummyJson, now));

        client.responseJson = "{\"elements\":[{\"type\":\"way\",\"id\":2,\"nodes\":[10,11],\"geometry\":[{\"lat\":48.137,\"lon\":11.575},{\"lat\":48.138,\"lon\":11.576}]}]}";

        Executor directExecutor = Runnable::run;
        RoadDataSource source = new RoadDataSource(store, client, directExecutor, clock);

        source.tilesAround(lat, lon, 0f, true);
        assertEquals(1, client.fetchCount);
        assertNotNull(store.read(t));

        // After fetch, subsequent tilesAround should find the parsed tile in memory/store
        List<RoadTile> tiles = source.tilesAround(lat, lon, 0f, false);
        boolean foundWay2 = false;
        for (RoadTile rt : tiles) {
            for (RoadTile.Way w : rt.ways) {
                if (w.id == 2L) {
                    foundWay2 = true;
                    break;
                }
            }
        }
        assertTrue(foundWay2);
    }

    @Test
    public void noNetworkWhenLiveNotAllowed() {
        double lat = 48.137;
        double lon = 11.575;
        Executor directExecutor = Runnable::run;
        RoadDataSource source = new RoadDataSource(store, client, directExecutor, clock);

        List<RoadTile> tiles = source.tilesAround(lat, lon, 0f, false);
        assertTrue(tiles.isEmpty());
        assertEquals(0, client.fetchCount);
    }

    @Test
    public void badJsonIsNotCached() {
        TileId t = new TileId(100, 200);
        client.responseJson = "<html>502 Bad Gateway</html>";

        Executor directExecutor = Runnable::run;
        RoadDataSource source = new RoadDataSource(store, client, directExecutor, clock);

        boolean ok = source.download(t);
        assertFalse(ok);
        assertNull(store.read(t));
        assertTrue(store.pending().contains(t));
    }

    @Test
    public void backsOffAfterFailure() {
        double lat = 48.137;
        double lon = 11.575;
        client.shouldThrow = true;

        Executor directExecutor = Runnable::run;
        RoadDataSource source = new RoadDataSource(store, client, directExecutor, clock);

        // First attempt fails and starts backoff
        source.tilesAround(lat, lon, 0f, true);
        assertEquals(1, client.fetchCount);

        // Second call within back-off period (10 seconds later) does not fetch
        now += 10_000L;
        source.tilesAround(lat, lon, 0f, true);
        assertEquals(1, client.fetchCount);
    }

    @Test
    public void singleInFlight() throws Exception {
        double lat = 48.137;
        double lon = 11.575;
        client.enterLatch = new CountDownLatch(1);
        client.blockLatch = new CountDownLatch(1);

        Executor pool = Executors.newFixedThreadPool(2);
        RoadDataSource source = new RoadDataSource(store, client, pool, clock);

        // Start first fetch in background thread
        source.tilesAround(lat, lon, 0f, true);

        // Wait until background thread enters fetch
        assertTrue(client.enterLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, client.fetchCount);

        // Second call while first is in flight should not trigger another fetch
        source.tilesAround(lat, lon, 0f, true);
        assertEquals(1, client.fetchCount);

        // Release first fetch
        client.blockLatch.countDown();
    }
}
