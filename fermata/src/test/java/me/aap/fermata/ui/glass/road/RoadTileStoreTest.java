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
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class RoadTileStoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File storeDir;
    private SharedPreferences prefs;
    private RoadTileStore store;

    @Before
    public void setUp() throws IOException {
        storeDir = tempFolder.newFolder("road_tiles");
        Context ctx = RuntimeEnvironment.getApplication();
        prefs = ctx.getSharedPreferences("test_road_tiles", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new RoadTileStore(storeDir, prefs);
    }

    @Test
    public void roundTrip() {
        TileId t = new TileId(100, 200);
        long now = 1000_000_000L;
        String json = "{\"elements\":[]}";

        assertTrue(store.put(t, json, now));
        assertEquals(json, store.read(t));
        assertTrue(store.fetchedAt(t) > 0);
        assertTrue(store.isFresh(t, now));

        long later = now + 31L * 24 * 3600_000L;
        assertFalse(store.isFresh(t, later));

        List<TileId> stale = store.staleTiles(later);
        assertEquals(1, stale.size());
        assertEquals(t, stale.get(0));
    }

    @Test
    public void pendingAndFrequent() {
        TileId t1 = new TileId(10, 20);
        TileId t2 = new TileId(30, 40);

        store.addPending(t1);
        Set<TileId> pending = store.pending();
        assertTrue(pending.contains(t1));

        store.removePending(t1);
        assertFalse(store.pending().contains(t1));

        assertEquals(1, store.recordTripEnd(t1));
        assertFalse(store.frequentPlaces().contains(t1));

        assertEquals(2, store.recordTripEnd(t1));
        assertTrue(store.frequentPlaces().contains(t1));

        assertEquals(1, store.recordTripEnd(t2));
        assertFalse(store.frequentPlaces().contains(t2));
    }

    @Test
    public void clearAllRemovesEverything() {
        TileId t = new TileId(10, 20);
        store.put(t, "{}", 1000L);
        store.addPending(t);
        store.recordTripEnd(t);
        store.recordTripEnd(t);

        store.clearAll();

        assertNull(store.read(t));
        assertEquals(-1, store.fetchedAt(t));
        assertTrue(store.pending().isEmpty());
        assertTrue(store.frequentPlaces().isEmpty());
        assertTrue(store.staleTiles(1000L).isEmpty());
    }

    @Test
    public void writeFailureLeavesTileMissing() throws IOException {
        File badDir = tempFolder.newFile("not_a_directory");
        RoadTileStore badStore = new RoadTileStore(badDir, prefs);
        TileId t = new TileId(5, 5);

        assertFalse(badStore.put(t, "{}", 1000L));
        assertEquals(-1, badStore.fetchedAt(t));
        assertNull(badStore.read(t));
    }
}
