package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.*;

import android.location.Location;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class GlassSpeedoTripControllerTest {

    @Test
    public void testDistanceAndAverageSpeedComputation() {
        GlassSpeedoTripController.TripState state = new GlassSpeedoTripController.TripState();
        state.addDistance(10000f, 600); // 10 km in 10 minutes (600s)
        assertEquals(10.0f, state.getDistanceKm(), 0.01f);
        assertEquals(60.0f, state.getAverageSpeedKmh(), 0.1f);
        assertEquals("00h 10m", state.getFormattedDriveTime());
    }

    @Test
    public void testMetricAndImperialFormatting() {
        // 100 km/h = ~62.14 mph
        assertEquals("100 km/h", GlassSpeedoTripController.formatSpeed(100f, true));
        assertEquals("62 mph", GlassSpeedoTripController.formatSpeed(100f, false));

        // 10 km = ~6.21 mi
        assertEquals("10.0 km", GlassSpeedoTripController.formatDistance(10000f, true));
        assertEquals("6.2 mi", GlassSpeedoTripController.formatDistance(10000f, false));
    }

    @Test
    public void testSpeedFilteringUnderThreshold() {
        AtomicReference<Float> reportedSpeed = new AtomicReference<>(-1f);
        GlassSpeedoTripController controller = new GlassSpeedoTripController(
                RuntimeEnvironment.getApplication(),
                (speedKmh, state) -> reportedSpeed.set(speedKmh)
        );

        Location slowLoc = new Location("gps");
        // 0.5 m/s = 1.8 km/h (< 3 km/h threshold)
        slowLoc.setSpeed(0.5f);
        slowLoc.setTime(System.currentTimeMillis());

        controller.onLocationReceived(slowLoc);
        assertEquals(0f, reportedSpeed.get(), 0.01f);

        Location fastLoc = new Location("gps");
        // 20 m/s = 72 km/h
        fastLoc.setSpeed(20f);
        fastLoc.setTime(System.currentTimeMillis() + 5000);

        controller.onLocationReceived(fastLoc);
        assertEquals(72f, reportedSpeed.get(), 0.1f);
    }

    @Test
    public void testTripReset() {
        GlassSpeedoTripController.TripState state = new GlassSpeedoTripController.TripState();
        state.addDistance(5000f, 300);
        assertTrue(state.getDistanceKm() > 0);
        assertTrue(state.getDriveTimeSeconds() > 0);

        state.reset();
        assertEquals(0f, state.getDistanceKm(), 0.001f);
        assertEquals(0f, state.getAverageSpeedKmh(), 0.001f);
        assertEquals(0, state.getDriveTimeSeconds());
        assertEquals("00h 00m", state.getFormattedDriveTime());
    }

    @Test
    public void testSpeedoTripViewInstantiationAndUpdate() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(RuntimeEnvironment.getApplication());
        assertNotNull(view);

        GlassSpeedoTripController.TripState state = new GlassSpeedoTripController.TripState();
        state.addDistance(12000f, 720);

        view.updateSpeed(65f, state, true);
        view.setSpeedLimit(80);

        // Switch to imperial
        view.updateSpeed(65f, state, false);
        view.setSpeedLimit(50);

        // Test unmeasured / no GPS state (R12)
        view.updateSpeed(-1f, state, true);
        view.refreshAppearance();
    }

    @Test
    public void testInitialSpeedUnmeasured() {
        AtomicReference<Float> reportedSpeed = new AtomicReference<>(0f);
        GlassSpeedoTripController controller = new GlassSpeedoTripController(
                RuntimeEnvironment.getApplication(),
                (speedKmh, state) -> reportedSpeed.set(speedKmh)
        );
        controller.start();
        assertEquals(-1f, reportedSpeed.get(), 0.01f);
    }
}
