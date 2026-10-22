package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/**
 * Whole-drive scenarios at 1 Hz, fed like the controller heartbeat (accept, tick, motion).
 * Distances are along a meridian so the truth is simply speed x time.
 */
public class TripScenarioTest {
    private static final long T0 = 1_000_000;
    private static final double M_PER_DEG = 111_320.0;

    /** What the receiver delivers in a given second. */
    private enum Kind { DOPPLER, NO_DOPPLER, NONE, COARSE }

    private interface Plan { Kind at(int second); }

    private static TripEngine.Fix fix(long t, double meters, double acc, double speed, double speedAcc) {
        return new TripEngine.Fix(t, 10.0 + meters / M_PER_DEG, 106.0, acc, speed, speedAcc);
    }

    private static TripEngine drive(double v, int seconds, Plan plan) {
        TripEngine e = new TripEngine();
        e.start(T0);
        double pos = 0;
        for (int i = 1; i <= seconds; i++) {
            long t = T0 + i * 1_000L;
            pos += v;
            switch (plan.at(i)) {
                case DOPPLER -> e.accept(fix(t, pos, 5, v, 0.3), t);
                case NO_DOPPLER -> e.accept(fix(t, pos, 5, Double.NaN, Double.NaN), t);
                case COARSE -> e.accept(fix(t, pos + 80, 150, Double.NaN, Double.NaN), t);
                case NONE -> { }
            }
            e.tick(t);
            e.motion(t, false, Double.NaN);
        }
        return e;
    }

    private static void assertDistance(double truth, TripEngine e, double tolerancePct) {
        double d = e.state().distanceMeters();
        assertTrue("distance " + d + " vs truth " + truth,
                Math.abs(d - truth) <= truth * tolerancePct / 100.0);
    }

    @Test public void steadyDopplerBaseline() {
        TripEngine e = drive(15, 600, i -> Kind.DOPPLER);
        assertDistance(9_000, e, 1);
        assertFalse(e.state().incomplete());
    }

    @Test public void interleavedMissingDoppler10pct() {
        TripEngine e = drive(15, 600, i -> i % 10 == 0 ? Kind.NO_DOPPLER : Kind.DOPPLER);
        assertDistance(9_000, e, 2);
        assertFalse(e.state().incomplete());
    }

    @Test public void interleavedMissingDoppler20pct() {
        TripEngine e = drive(15, 600, i -> i % 5 == 0 ? Kind.NO_DOPPLER : Kind.DOPPLER);
        assertDistance(9_000, e, 2);
        assertFalse(e.state().incomplete());
    }

    @Test public void slowTrafficWithNoDopplerEveryOtherSecond() {
        TripEngine e = drive(4, 600, i -> i % 2 == 0 ? Kind.NO_DOPPLER : Kind.DOPPLER);
        assertDistance(2_400, e, 2);
    }

    @Test public void lowSpeedPoorSpeedAccuracy() {
        for (double[] c : new double[][] {{3, 0.5, 1.5}, {5, 0.5, 2.0}, {10, 0.5, 4.0}}) {
            Random r = new Random(7);
            TripEngine e = new TripEngine();
            e.start(T0);
            double pos = 0;
            for (int i = 1; i <= 600; i++) {
                long t = T0 + i * 1_000L;
                pos += c[0];
                double sigma = c[1] + r.nextDouble() * (c[2] - c[1]);
                double v = Math.max(0, c[0] + r.nextGaussian() * sigma * 0.5);
                e.accept(fix(t, pos, 5, v, sigma), t);
                e.tick(t);
            }
            assertDistance(c[0] * 600, e, 3);
        }
    }

    @Test public void positionsOnlyNoDoppler() {
        assertDistance(9_000, drive(15, 600, i -> Kind.NO_DOPPLER), 2);
        assertDistance(2_400, drive(4, 600, i -> Kind.NO_DOPPLER), 2);
    }

    @Test public void positionsOnlyDriveNeverFlickersToZero() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 120; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 4.0, 5, Double.NaN, Double.NaN), t);
            e.tick(t);
            if (i > 10) assertTrue("second " + i, e.state().speedKmh() != 0f);
        }
    }

    @Test public void tunnelNoFix120s() {
        TripEngine e = drive(15, 600, i -> (i > 200 && i <= 320) ? Kind.NONE : Kind.DOPPLER);
        assertDistance(9_000, e, 2);
        assertFalse(e.state().incomplete());
    }

    @Test public void tunnelWithCoarseFixes() {
        TripEngine e = drive(15, 600, i -> (i > 200 && i <= 210) ? Kind.NONE
                : (i > 210 && i <= 320) ? Kind.COARSE : Kind.DOPPLER);
        assertDistance(9_000, e, 2);
        assertFalse(e.state().incomplete());
    }

    @Test public void stoppedInsideTunnel() {
        TripEngine e = new TripEngine();
        e.start(T0);
        double pos = 0;
        float kmAtEntry = 0;
        for (int i = 1; i <= 800; i++) {
            long t = T0 + i * 1_000L;
            boolean moving = !(i > 210 && i <= 510);
            if (moving) pos += 15;
            boolean noFix = i > 200 && i <= 520;
            if (!noFix) e.accept(fix(t, pos, 5, moving ? 15 : 0, 0.3), t);
            e.tick(t);
            // Idle-engine vibration: the IMU never reports a stop.
            e.motion(t, false, Double.NaN);
            if (i == 200) kmAtEntry = e.state().getDistanceKm();
            if (i == 500) {
                assertEquals(-1, e.state().speedKmh(), 0);
                assertEquals(kmAtEntry, e.state().getDistanceKm(), 0.001f);
            }
        }
        assertDistance(15 * 500, e, 3);
    }

    @Test public void suspendDoesNotCountTime() {
        TripEngine e = new TripEngine();
        e.start(T0);
        double pos = 0;
        for (int i = 1; i <= 600; i++) {
            long t = T0 + i * 1_000L;
            pos += 15;
            e.accept(fix(t, pos, 5, 15, 0.3), t);
            e.tick(t);
        }
        long off = T0 + 600_000;
        e.suspend(off);
        assertTrue(e.state().suspended());
        assertFalse(e.state().isPaused());
        long back = off + 3_600_000;
        e.start(back);
        pos += 5_000; // parked somewhere else meanwhile: must not be bridged
        for (int i = 1; i <= 600; i++) {
            long t = back + i * 1_000L;
            pos += 15;
            e.accept(fix(t, pos, 5, 15, 0.3), t);
            e.tick(t);
        }
        TripEngine.State s = e.state();
        assertEquals(1_200_000, s.totalTimeMs(), 2_000);
        assertEquals(54, s.getAverageSpeedKmh(), 1);
        assertDistance(18_000, e, 1);
    }

    @Test public void autoNewTripAfterLongSuspend() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 60; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 15, 5, 15, 0.3), t);
            e.tick(t);
        }
        double firstTrip = e.state().distanceMeters();
        e.suspend(T0 + 60_000);
        e.start(T0 + 60_000 + TripEngine.AUTO_NEW_TRIP_MS + 1);
        assertEquals(0, e.state().distanceMeters(), 0);
        assertEquals(0, e.state().totalTimeMs());
        assertEquals(firstTrip, e.previousTrip().distanceMeters(), 0.001);
    }

    @Test public void shortSuspendKeepsTheTrip() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 60; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 15, 5, 15, 0.3), t);
            e.tick(t);
        }
        double d = e.state().distanceMeters();
        e.suspend(T0 + 60_000);
        e.start(T0 + 60_000 + 30 * 60_000);
        assertEquals(d, e.state().distanceMeters(), 0.001);
        assertEquals(null, e.previousTrip());
    }

    @Test public void restoredSuspendedTripStartsNewTripAfterLongOfflineTime() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 60; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 15, 5, 15, 0.3), t);
            e.tick(t);
        }
        e.suspend(T0 + 60_000);
        TripEngine restored = new TripEngine();
        restored.restore(e.state(), TripEngine.AUTO_NEW_TRIP_MS);
        assertFalse(restored.state().incomplete());
        restored.start(5_000);
        assertEquals(0, restored.state().distanceMeters(), 0);
        assertTrue(restored.previousTrip().distanceMeters() > 0);
    }

    @Test public void unknownSpeedWhileTrackingIsNotZero() {
        TripEngine e = new TripEngine();
        e.start(T0);
        e.accept(fix(T0 + 1_000, 0, 5, 15, 0.3), T0 + 1_000);
        e.accept(fix(T0 + 2_000, 15, 5, Double.NaN, Double.NaN), T0 + 2_000);
        assertEquals(54, e.state().speedKmh(), 0.001);
        for (int i = 3; i <= 6; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, (i - 1) * 15, 30, Double.NaN, Double.NaN), t);
            e.tick(t);
        }
        assertEquals(-1, e.state().speedKmh(), 0);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
    }

    @Test public void firstFixSpikeDoesNotSetMaxOrDistance() {
        TripEngine e = new TripEngine();
        e.start(T0);
        e.accept(fix(T0 + 1_000, 0, 5, 60, 0.5), T0 + 1_000);
        for (int i = 2; i <= 30; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, (i - 1) * 10, 5, 10, 0.3), t);
            e.tick(t);
        }
        assertTrue(e.state().maxSpeedKmh() <= 37.8f);
        assertEquals(290, e.state().distanceMeters(), 15);
    }

    @Test public void parkedJitterNoDistanceNoIncomplete() {
        Random r = new Random(1);
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 120; i++) {
            long t = T0 + i * 1_000L;
            double speed = r.nextDouble() < 0.3 ? Double.NaN : 0.1;
            e.accept(fix(t, (r.nextDouble() - 0.5) * 12, 8, speed, 0.5), t);
            e.tick(t);
        }
        assertEquals(0, e.state().distanceMeters(), 0);
        assertFalse(e.state().incomplete());
        assertEquals(0, e.state().speedKmh(), 0);
    }

    @Test public void staleSpeedEstimatedThenHidden() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 10; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 15, 5, 15, 0.3), t);
            e.tick(t);
        }
        long lastFix = T0 + 10_000;
        e.tick(lastFix + 2_000);
        e.motion(lastFix + 2_000, false, Double.NaN);
        assertEquals(54, e.state().speedKmh(), 0.001);
        assertFalse(e.state().speedEstimated());
        e.tick(lastFix + 5_000);
        e.motion(lastFix + 5_000, false, Double.NaN);
        assertEquals(54, e.state().speedKmh(), 0.001);
        assertTrue(e.state().speedEstimated());
        e.tick(lastFix + 9_000);
        e.motion(lastFix + 9_000, false, Double.NaN);
        assertEquals(-1, e.state().speedKmh(), 0);
        assertFalse(e.state().speedEstimated());
    }

    @Test public void firstGoodDopplerAfterTunnelShowsImmediately() {
        TripEngine e = new TripEngine();
        e.start(T0);
        for (int i = 1; i <= 10; i++) {
            long t = T0 + i * 1_000L;
            e.accept(fix(t, i * 15, 5, 15, 0.3), t);
            e.tick(t);
        }
        long exit = T0 + 70_000;
        e.tick(exit);
        assertEquals(-1, e.state().speedKmh(), 0);
        assertFalse(e.accept(fix(exit, 1_050, 5, 20, 0.3), exit));
        assertEquals(72, e.state().speedKmh(), 0.001);
    }

    @Test public void pauseStillExcludesTimeAndIsShownAsPaused() {
        TripEngine e = new TripEngine();
        e.start(T0);
        e.accept(fix(T0 + 1_000, 0, 5, 10, 0.3), T0 + 1_000);
        e.pause(T0 + 5_000);
        assertTrue(e.state().isPaused());
        assertFalse(e.state().suspended());
    }
}
