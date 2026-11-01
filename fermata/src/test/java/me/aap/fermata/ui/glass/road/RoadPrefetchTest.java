package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowJobScheduler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import me.aap.fermata.ui.activity.MainActivityPrefs;

@RunWith(RobolectricTestRunner.class)
public class RoadPrefetchTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;
    private File storeDir;
    private SharedPreferences prefs;
    private RoadTileStore store;
    private JobScheduler jobScheduler;
    private ShadowJobScheduler shadowJobScheduler;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        storeDir = new File(context.getNoBackupFilesDir(), "road_tiles");
        if (storeDir.exists()) {
            File[] files = storeDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
        prefs = context.getSharedPreferences("road_tiles", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = RoadTileStore.get(context);

        jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        shadowJobScheduler = Shadows.shadowOf(jobScheduler);
        jobScheduler.cancelAll();

        MainActivityPrefs.get().applyBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA, true);
    }

    @Test
    public void tripEndCountsAndSchedules() {
        Location loc = new Location("gps");
        loc.setLatitude(48.137);
        loc.setLongitude(11.575);
        TileId t = TileId.of(48.137, 11.575);

        // First trip end
        RoadPrefetch.onTripEnded(context, loc);
        assertFalse(store.frequentPlaces().contains(t));

        // Second trip end in same tile -> frequent place
        RoadPrefetch.onTripEnded(context, loc);
        assertTrue(store.frequentPlaces().contains(t));

        JobInfo catchUp = shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_CATCH_UP);
        assertNotNull(catchUp);
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, catchUp.getNetworkType());
        assertFalse(catchUp.isPersisted());

        JobInfo refresh = shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_REFRESH);
        assertNotNull(refresh);
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, refresh.getNetworkType());
        assertTrue(refresh.isRequireCharging());
        assertTrue(refresh.isRequireDeviceIdle());
        assertTrue(refresh.isPeriodic());
        assertFalse(refresh.isPersisted());
    }

    @Test
    public void catchUpWorkCoversPendingAndFrequentPlaces() {
        TileId pendingTile = new TileId(500, 500);
        store.addPending(pendingTile);

        TileId frequentTile = TileId.of(48.137, 11.575);
        store.recordTripEnd(frequentTile);
        store.recordTripEnd(frequentTile);
        assertTrue(store.frequentPlaces().contains(frequentTile));

        // Store one tile that is within 50 km of frequentTile so it gets excluded from catch-up
        long now = 1000_000_000L;
        store.put(frequentTile, "{\"elements\":[]}", now);

        List<TileId> work = RoadPrefetch.catchUpWork(store);
        assertNotNull(work);
        assertTrue(work.contains(pendingTile));
        // Already stored frequentTile should be excluded
        assertFalse(work.contains(frequentTile));
        // Other tiles within 50 km should be present
        assertTrue(work.size() > 10);
    }

    @Test
    public void refreshWorkTakesAThirtieth() {
        long now = 1000_000_000_000L;
        long staleTime = now - (RoadTileStore.FRESH_MS + 10_000L);

        // Put 60 stale tiles with decreasing timestamps (oldest first: tile 0 has lowest mtime)
        for (int i = 0; i < 60; i++) {
            TileId t = new TileId(1000 + i, 2000);
            store.put(t, "{\"elements\":[]}", staleTime - (60 - i) * 1000L);
        }

        List<TileId> work = RoadPrefetch.refreshWork(store, now);
        assertNotNull(work);
        assertEquals(2, work.size());
        assertEquals(new TileId(1000, 2000), work.get(0));
        assertEquals(new TileId(1001, 2000), work.get(1));
    }

    @Test
    public void disableCancelsAndClears() {
        Location loc = new Location("gps");
        loc.setLatitude(48.137);
        loc.setLongitude(11.575);
        RoadPrefetch.onTripEnded(context, loc);
        assertNotNull(shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_CATCH_UP));

        TileId t = TileId.of(48.137, 11.575);
        store.put(t, "{\"elements\":[]}", System.currentTimeMillis());

        RoadPrefetch.disable(context);

        assertNull(shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_CATCH_UP));
        assertNull(shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_REFRESH));
        assertNull(store.read(t));
        assertTrue(store.pending().isEmpty());
        assertTrue(store.frequentPlaces().isEmpty());
    }

    @Test
    public void offPrefSchedulesNothing() {
        MainActivityPrefs.get().applyBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA, false);

        Location loc = new Location("gps");
        loc.setLatitude(48.137);
        loc.setLongitude(11.575);
        RoadPrefetch.onTripEnded(context, loc);
        RoadPrefetch.scheduleCatchUp(context);
        RoadPrefetch.scheduleRefresh(context);

        assertNull(shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_CATCH_UP));
        assertNull(shadowJobScheduler.getPendingJob(RoadPrefetch.JOB_REFRESH));
    }
}
