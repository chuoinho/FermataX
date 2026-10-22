package me.aap.fermata.carfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.location.Location;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class ParkingLocationSmootherTest {

	private Location createLocation(double lat, double lon, float accuracy, float speed, long time) {
		Location loc = new Location("gps");
		loc.setLatitude(lat);
		loc.setLongitude(lon);
		loc.setAccuracy(accuracy);
		loc.setSpeed(speed);
		loc.setTime(time);
		return loc;
	}

	@Test
	public void outdoorStationaryClusteringYieldsHighPrecision() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		// 4 stationary GPS fixes around T0 with small jitter (acc = 6m)
		smoother.recordLocationSample(createLocation(21.00001, 105.00001, 6.0f, 0.0f, t0 - 3000L));
		smoother.recordLocationSample(createLocation(21.00002, 105.00002, 6.0f, 0.0f, t0 - 2000L));
		smoother.recordLocationSample(createLocation(21.00000, 105.00000, 6.0f, 0.0f, t0 - 1000L));
		smoother.recordLocationSample(createLocation(21.00001, 105.00001, 6.0f, 0.0f, t0));

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, null);

		assertNotNull(result);
		// Accuracy converges to sqrt(1.0 / sum(1.0 / 6^2)) = sqrt(1.0 / (4/36)) = 3.0m < 5.0m
		assertTrue("Accuracy should be < 5.0m, but was: " + result.accuracy, result.accuracy < 5.0f);
		assertEquals("high", result.confidenceLevel);
		assertEquals(21.00001, result.latitude, 1e-5);
		assertEquals(105.00001, result.longitude, 1e-5);
	}

	@Test
	public void basementOutageLocksToRampEntrance() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		// Good GPS fix at T0 - 30s (lat=21.0005, lon=105.0005, acc=8m)
		smoother.recordLocationSample(createLocation(21.0005, 105.0005, 8.0f, 5.0f, t0 - 30_000L));

		// Coarse cell tower fixes at T0 - 5s and T0 (lat=21.0100, lon=105.0100, acc=200m)
		smoother.recordLocationSample(createLocation(21.0100, 105.0100, 200.0f, 0.0f, t0 - 5_000L));
		smoother.recordLocationSample(createLocation(21.0100, 105.0100, 200.0f, 0.0f, t0));

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, null);

		assertNotNull(result);
		// Result locks to the ramp entrance at (21.0005, 105.0005) with confidence "basement"
		assertEquals("basement", result.confidenceLevel);
		assertEquals(21.0005, result.latitude, 1e-6);
		assertEquals(105.0005, result.longitude, 1e-6);
		assertEquals(8.0f, result.accuracy, 1e-3f);

		// Distant cell tower fix (200m) must NOT drag or overwrite the ramp entrance fix!
		assertNotEquals(21.0100, result.latitude, 1e-3);
		assertNotEquals(105.0100, result.longitude, 1e-3);
	}

	@Test
	public void fallbackLocationUsedWhenBufferEmpty() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		Location fallbackLoc = createLocation(21.0200, 105.0200, 10.0f, 0.0f, t0);

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, fallbackLoc);

		assertNotNull(result);
		assertEquals(21.0200, result.latitude, 1e-6);
		assertEquals(105.0200, result.longitude, 1e-6);
		assertEquals(10.0f, result.accuracy, 1e-3f);
		assertEquals("high", result.confidenceLevel);
	}

	@Test
	public void singleFixFallsBackToVehicularCandidate() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		smoother.recordLocationSample(createLocation(21.0005, 105.0005, 10.0f, 0.0f, t0 - 1000L));

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, null);

		assertNotNull(result);
		assertEquals(21.0005, result.latitude, 1e-6);
		assertEquals(105.0005, result.longitude, 1e-6);
		assertEquals(10.0f, result.accuracy, 1e-3f);
		assertEquals("high", result.confidenceLevel);
	}

	@Test
	public void movingFixesExcludedFromCentroid() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		// Moving fix v = 10 m/s
		smoother.recordLocationSample(createLocation(21.0050, 105.0050, 5.0f, 10.0f, t0 - 5000L));
		// Stationary fix v = 0 m/s
		smoother.recordLocationSample(createLocation(21.0001, 105.0001, 8.0f, 0.0f, t0));

		// Only 1 stationary fix exists, falls back to vehicular candidate
		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, null);

		assertNotNull(result);
		assertEquals(21.0001, result.latitude, 1e-4);
		assertEquals(105.0001, result.longitude, 1e-4);
	}

	@Test
	public void emptyBufferWithoutFallbackReturnsLowConfidenceDefault() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(t0, null);

		assertNotNull(result);
		assertEquals(0.0, result.latitude, 1e-6);
		assertEquals(0.0, result.longitude, 1e-6);
		assertEquals("low", result.confidenceLevel);
	}

	@Test
	public void staleFixesPurgedFromBuffer() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		// Fix 130 seconds before t0 (older than 120s MAX_BUFFER_AGE_MS)
		smoother.recordLocationSample(createLocation(21.1111, 105.1111, 5.0f, 0.0f, t0 - 130_000L));
		// Fix at t0
		smoother.recordLocationSample(createLocation(21.2222, 105.2222, 8.0f, 0.0f, t0));

		assertEquals(1, smoother.getLocationFixHistory().size());
		assertEquals(21.2222, smoother.getLocationFixHistory().get(0).latitude, 1e-4);
	}

	@Test
	public void clearResetsBuffer() {
		ParkingLocationSmoother smoother = new ParkingLocationSmoother();
		long t0 = 1_000_000_000L;

		smoother.recordLocationSample(createLocation(21.0001, 105.0001, 8.0f, 0.0f, t0));
		assertEquals(1, smoother.getLocationFixHistory().size());

		smoother.clear();
		assertEquals(0, smoother.getLocationFixHistory().size());
	}
}
