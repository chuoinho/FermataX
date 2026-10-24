package me.aap.fermata.ui.glass.speedo;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripEngineTest {
    private static TripEngine engine() {
        TripEngine e = new TripEngine();
        e.start(1_000);
        return e;
    }
    private static TripEngine.Fix fix(long time, double meters, double speed) {
        return new TripEngine.Fix(time, 0, Math.toDegrees(meters / 6_371_000), 3, speed, 0.1);
    }
    private static void feed(TripEngine e, long time, double meters, double speed) {
        e.accept(fix(time, meters, speed), time);
    }

    @Test public void integratesSubsecondSamplesWithoutTruncation() {
        TripEngine e = engine();
        for (int i = 0; i <= 100; i++) feed(e, 1_000 + i * 200, i * 2, 10);
        assertEquals(200, e.state().distanceMeters(), 0.001);
        assertEquals(20_000, e.state().totalTimeMs());
        assertEquals(36, e.state().getAverageSpeedKmh(), 0.001);
    }

    @Test public void totalAverageIncludesRedLightWithoutAddingStationaryDrift() {
        TripEngine e = engine();
        for (int i = 0; i <= 10; i++) feed(e, 1_000 + i * 1_000, i * 5, 5);
        feed(e, 12_000, 52.5, 0);
        double before = e.state().distanceMeters();
        for (int i = 13; i <= 72; i++) feed(e, i * 1_000, 52.5 + (i % 2), 0);
        assertEquals(before, e.state().distanceMeters(), 0);
        assertEquals(71_000, e.state().totalTimeMs());
        assertEquals(before * 3_600 / 71_000, e.state().getAverageSpeedKmh(), 0.001);
    }

    @Test public void slowCrawlingIsNotDiscardedByOldThreeKmhThreshold() {
        TripEngine e = engine();
        for (int i = 0; i <= 60; i++) feed(e, 1_000 + i * 1_000, i * 0.5, 0.5);
        assertEquals(30, e.state().distanceMeters(), 0.001);
        assertEquals(1.8f, e.state().speedKmh(), 0.001f);
    }

    @Test public void missingSpeedIsUnknownUntilPositionWindowHasEvidence() {
        TripEngine e = engine();
        feed(e, 1_000, 0, Double.NaN);
        feed(e, 2_000, 10, Double.NaN);
        assertEquals(-1, e.state().speedKmh(), 0);
        feed(e, 3_000, 20, Double.NaN);
        feed(e, 4_000, 30, Double.NaN);
        assertEquals(36, e.state().speedKmh(), 0.001);
        assertEquals(30, e.state().distanceMeters(), 0.001);
        assertEquals(30, e.state().estimatedMeters(), 0.001);
    }

    @Test public void weakPositionNoiseNeverBecomesFallbackSpeed() {
        TripEngine e = engine();
        for (int i = 0; i <= 60; i++) {
            TripEngine.Fix f = new TripEngine.Fix(1_000 + i * 1_000, 0,
                    Math.toDegrees((i % 2) * 10 / 6_371_000.0), 30, Double.NaN, Double.NaN);
            e.accept(f, f.timeMs());
        }
        assertEquals(0, e.state().distanceMeters(), 0);
        assertEquals(-1, e.state().speedKmh(), 0);
    }

    @Test public void staleSamplesAndDuplicateCallbacksDoNotKeepSpeedAlive() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        assertFalse(e.accept(fix(1_000, 100, 40), 2_000));
        assertFalse(e.accept(fix(500, 100, 40), 2_000));
        assertFalse(e.accept(fix(1_000, 100, 40), 10_000));
        assertEquals(-1, e.state().speedKmh(), 0);
        assertEquals(TripEngine.Signal.STALE, e.state().signal());
        assertEquals(0, e.state().distanceMeters(), 0);
    }

    @Test public void rejectsMissingAccuracyFutureTimeAndNonFiniteCoordinates() {
        TripEngine e = engine();
        assertFalse(e.accept(new TripEngine.Fix(1_000, 0, 0, Double.NaN, 10, 1), 1_000));
        assertFalse(e.accept(new TripEngine.Fix(1_000, Double.NaN, 0, 3, 10, 1), 1_000));
        assertFalse(e.accept(fix(2_000, 0, 10), 1_000));
        assertEquals(0, e.state().distanceMeters(), 0);
    }

    @Test public void rejectsTeleportAndSingleSpeedSpike() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        assertFalse(e.accept(fix(2_000, 5_000, 10), 2_000));
        assertFalse(e.accept(fix(2_000, 10, 60), 2_000));
        // Max speed needs two consecutive Doppler readings, so the rejected spike never counts.
        assertTrue(e.state().maxSpeedKmh() <= 36);
        feed(e, 3_000, 20, 10);
        feed(e, 4_000, 30, 10);
        feed(e, 5_000, 40, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);
        assertEquals(36, e.state().maxSpeedKmh(), 0.001);
    }

    @Test public void watchdogDoesNotIntegrateOldSpeedThroughTunnel() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        e.tick(65_000);
        assertEquals(40, e.state().distanceMeters(), 0.001);
        assertEquals(-1, e.state().speedKmh(), 0);
        assertTrue(e.state().incomplete());
    }

    @Test public void bridgesTunnelOnlyAfterThreeFixesAndDoesNotDoubleCount() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        feed(e, 65_000, 640, 10);
        feed(e, 66_000, 650, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);
        feed(e, 67_000, 660, 10);
        // chord = 620, est = 620 * 1.05 = 651
        assertEquals(40 + 651, e.state().distanceMeters(), 0.001);
        assertEquals(651, e.state().estimatedMeters(), 0.001);
        assertFalse(e.state().incomplete());
        for (int i = 1; i <= 3; i++) feed(e, 67_000 + i * 1_000, 660 + i * 10, 10);
        assertEquals(691 + 30, e.state().distanceMeters(), 0.001);
        assertEquals(651, e.state().estimatedMeters(), 0.001);
        assertEquals(-1, e.state().getMovingAverageSpeedKmh(), 0);
    }

    @Test public void returningNearSamePlaceDoesNotInventDistance() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 0);
        feed(e, 61_000, 0, 0);
        feed(e, 62_000, 1, 0);
        feed(e, 63_000, 2, 0);
        assertEquals(0, e.state().distanceMeters(), 0);
        assertTrue(e.state().incomplete());
    }

    @Test public void implausibleOrVeryLongGapIsNotBridged() {
        for (long gap : new long[] {30_000, 900_000}) {
            TripEngine e = engine();
            feed(e, 1_000, 0, 10);
            feed(e, gap, 50_000, 10);
            feed(e, gap + 1_000, 50_010, 10);
            feed(e, gap + 2_000, 50_020, 10);
            assertEquals(0, e.state().distanceMeters(), 0);
            assertTrue(e.state().incomplete());
        }
    }

    @Test public void badFirstReacquisitionDoesNotPoisonAnchor() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        feed(e, 61_000, 5_000, 10);
        feed(e, 62_000, 610, 10);
        feed(e, 63_000, 620, 10);
        feed(e, 64_000, 630, 10);
        // chord = 630, est = 630 * CURVE_PRIOR (1.05) = 661.5
        assertEquals(630 * 1.05, e.state().distanceMeters(), 0.001);
    }

    @Test public void resetClearsPreviousFixAndRecoveryAnchors() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        feed(e, 61_000, 600, 10);
        e.reset(62_000);
        feed(e, 63_000, 620, 10);
        assertEquals(0, e.state().distanceMeters(), 0);
        assertEquals(0, e.state().totalTimeMs());
        assertFalse(e.state().incomplete());
    }

    @Test public void explicitPauseExcludesPauseTimeAndNeverBridgesAcrossIt() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        e.pause(5_000);
        assertFalse(e.accept(fix(6_000, 50, 10), 6_000));
        e.start(65_000);
        feed(e, 65_000, 640, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);
        assertEquals(4_000, e.state().totalTimeMs());
    }

    @Test public void restoredActiveTripMarksUnobservedDataAndDoesNotReusePosition() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        TripEngine restored = new TripEngine();
        restored.restore(e.state());
        restored.start(80_000);
        feed(restored, 80_000, 790, 10);
        assertEquals(40, restored.state().distanceMeters(), 0.001);
        assertTrue(restored.state().incomplete());
        assertEquals(-1, restored.state().getMovingAverageSpeedKmh(), 0);
    }

    @Test public void fallbackToSensorTransitionDoesNotCountSameIntervalTwice() {
        TripEngine e = engine();
        for (int i = 0; i <= 3; i++) feed(e, 1_000 + i * 1_000, i * 10, Double.NaN);
        for (int i = 4; i <= 6; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        assertTrue(e.state().distanceMeters() <= 60.001);
        // The window commit (0..30 m) and the one-sided Doppler interval (30..40 m) cover
        // everything, so nothing is missing any more.
        assertEquals(60, e.state().distanceMeters(), 0.001);
        assertFalse(e.state().incomplete());
    }

    @Test public void singleMotionBlipWhileParkedAddsNoDistance() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 0);
        feed(e, 2_000, 0.5, 0.5);
        feed(e, 3_000, 0, 0);
        assertEquals(0, e.state().distanceMeters(), 0);
    }

    @Test public void cachedFixBeforeResetCannotStartNewTrip() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        e.reset(2_000);
        assertFalse(e.accept(fix(1_500, 5, 10), 2_500));
        feed(e, 3_000, 20, 10);
        assertEquals(0, e.state().distanceMeters(), 0);
    }

    @Test public void recoveryAcceptsSlowerCadenceWithoutRestartingConfirmation() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        feed(e, 61_000, 600, 10);
        feed(e, 65_000, 640, 10);
        feed(e, 69_000, 680, 10);
        // chord = 680, est = 680 * 1.05 = 714
        assertEquals(714, e.state().estimatedMeters(), 0.001);
    }

    @Test public void urbanCanyonHighAccuracyDopplerAccepted() {
        TripEngine e = engine();
        // Accuracy 45m (> 35m old threshold, <= 100m MAX_ACCURACY) with valid speed
        for (int i = 0; i <= 4; i++) {
            TripEngine.Fix f = new TripEngine.Fix(1_000 + i * 1_000, 0,
                    Math.toDegrees(i * 10.0 / 6_371_000), 45, 10, 0.2);
            assertTrue(e.accept(f, f.timeMs()));
        }
        assertEquals(40, e.state().distanceMeters(), 0.001);
        assertEquals(36, e.state().speedKmh(), 0.001);
    }

    @Test public void urbanCanyonHighAccuracyWithoutSpeedRejectedFromFallback() {
        TripEngine e = engine();
        // Accuracy 45m (> 20m positionTrusted) with NaN speed
        for (int i = 0; i <= 5; i++) {
            TripEngine.Fix f = new TripEngine.Fix(1_000 + i * 1_000, 0,
                    Math.toDegrees(i * 10.0 / 6_371_000), 45, Double.NaN, Double.NaN);
            e.accept(f, f.timeMs());
        }
        // Fallback position displacement must reject because !positionTrusted()
        assertEquals(0, e.state().distanceMeters(), 0);
        assertEquals(-1, e.state().speedKmh(), 0);
    }

    @Test public void zeroSpeedWithStationaryNoiseAcceptedAsZero() {
        TripEngine e = engine();
        TripEngine.Fix f = new TripEngine.Fix(1_000, 21.0, 105.0, 15.0, 0.0, 4.0);
        assertTrue(e.accept(f, 1_000));
        assertEquals(0f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
    }

    @Test public void coarseAccuracyFixesMaintainTrackingWithoutDrift() {
        TripEngine e = engine();
        // Accuracy 250m from cabin GPS or cell tower
        TripEngine.Fix f1 = new TripEngine.Fix(1_000, 21.0, 105.0, 250.0, 0.0, 4.0);
        assertTrue("Coarse fix up to 300m must be accepted to maintain tracking", e.accept(f1, 1_000));
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
        assertEquals(0f, e.state().speedKmh(), 0.001);
        assertEquals(0, e.state().distanceMeters(), 0);
    }

    @Test public void stoppedVehicleMaintainsZeroSpeedWhenFixLacksDoppler() {
        TripEngine e = engine();
        // Stopped vehicle receives 2 fixes with 0 speed
        feed(e, 1_000, 0, 0);
        feed(e, 2_000, 0, 0);
        assertEquals(0f, e.state().speedKmh(), 0.001);
        // Subsequent fix arrives without Doppler speed (e.g. idle GPS or fallback)
        TripEngine.Fix f3 = new TripEngine.Fix(3_000, 0, 0, 15.0, Double.NaN, Double.NaN);
        assertTrue(e.accept(f3, 3_000));
        assertEquals("Stationary vehicle must keep 0 km/h rather than dropping to -1",
                0f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
    }

    @Test public void tentativeGapUnderFreshCadenceDoesNotForceRecoveryOrStale() {
        TripEngine e = engine();
        for (int i = 0; i <= 3; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        // Tentative gap opened by heartbeat at 6,500ms (2.5s after last fix at 4,000ms)
        e.motion(6_500, false, Double.NaN);
        // Fresh fix arrives at 7,000ms (dt = 3,000ms <= FRESH_MS 5,000ms)
        TripEngine.Fix f = fix(7_000, 70, 10);
        assertTrue("Fresh fix under FRESH_MS must be accepted immediately without multi-step recovery",
                e.accept(f, 7_000));
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
        assertEquals(36f, e.state().speedKmh(), 0.001f);
    }

    @Test public void coarseToFineTransitionAcceptsImmediateFineFix() {
        TripEngine e = engine();
        // Initial coarse seed location (e.g. cell BTS, accuracy 120m)
        TripEngine.Fix coarse = new TripEngine.Fix(1_000, 21.0000, 105.0000, 120.0, Double.NaN, Double.NaN);
        assertTrue(e.accept(coarse, 1_000));
        // 1 second later, real GPS lock arrives 350m away with fine accuracy 15m
        double dLat = 350.0 / 111_000.0;
        TripEngine.Fix fineGps = new TripEngine.Fix(2_000, 21.0000 + dLat, 105.0000, 15.0, 0.0, 0.2);
        assertTrue("Fine GPS fix must be accepted immediately despite shift from coarse BTS anchor",
                e.accept(fineGps, 2_000));
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
        assertEquals(0f, e.state().speedKmh(), 0.001);
    }

    @Test public void curvedTunnelEstimatedWithinClampedPrior() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);

        // Circular 90-degree curve with radius R = 300m
        // Ground truth arc length = (pi / 2) * 300 = 471.24m
        // Straight chord between entrance and exit = sqrt(300^2 + 300^2) = 424.26m
        double chord = Math.sqrt(2) * 300;
        // Fixes at exit: tunnel end is reached, and 3 recovery fixes arrive
        feed(e, 50_000, 40 + chord, 10);
        feed(e, 51_000, 40 + chord + 10, 10);
        feed(e, 52_000, 40 + chord + 20, 10);

        double est = e.state().estimatedMeters();
        double totalChord = chord + 20; // Last fix at recovery completion is 20m past tunnel exit
        double u = Math.hypot(3, 3);
        assertTrue("Estimated distance must be >= chord - u", est >= totalChord - u);
        assertEquals(totalChord * 1.05, est, 0.01);
        assertTrue("Uncertainty must be positive", e.state().uncertaintyMeters() > 0);

        double groundTruthTravel = (Math.PI / 2.0) * 300 + 20; // 471.24m arc + 20m post-exit
        double errorPct = (est - groundTruthTravel) / groundTruthTravel * 100.0;
        // With CURVE_PRIOR=1.05 on 90 deg curve, error is approx -5.0% (underestimates sharp curve)
        assertTrue(errorPct < 0 && errorPct > -10.0);
    }

    @Test public void undergroundParkingAfterStopDoesNotMarkIncomplete() {
        TripEngine e = engine();
        // Drive for 4 seconds to establish motion
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);
        // Stop and stay stopped for 3 seconds -> stoppedCount >= 3
        feed(e, 6_000, 42.5, 0);
        feed(e, 7_000, 42.5, 0);
        feed(e, 8_000, 42.5, 0);
        double stoppedDistance = e.state().distanceMeters();
        double uBefore = e.state().uncertaintyMeters();
        // Parked in underground garage for 15 minutes (> MAX_BRIDGE_MS 10 minutes)
        long parkExitTime = 8_000 + 15 * 60_000;
        feed(e, parkExitTime, 43.0, 0);
        feed(e, parkExitTime + 1_000, 43.2, 0);
        feed(e, parkExitTime + 2_000, 42.8, 0);
        // Distance unchanged from when stopped, no estimated distance added
        assertEquals(stoppedDistance, e.state().distanceMeters(), 0.001);
        assertEquals(0, e.state().estimatedMeters(), 0.001);
        assertFalse(e.state().incomplete()); // A4: Not marked incomplete!
        // Displacement during parking is preserved in uncertainty
        assertTrue(e.state().uncertaintyMeters() > uBefore);
    }

    @Test public void singleStationaryFixBeforeGapMarksIncomplete() {
        TripEngine e = engine();
        // Stopped only 1 sample before gap (stoppedCount == 0, not confirmed stopped)
        feed(e, 1_000, 0, 0);
        long parkExitTime = 1_000 + 15 * 60_000;
        feed(e, parkExitTime, 0.5, 0);
        feed(e, parkExitTime + 1_000, 1.0, 0);
        feed(e, parkExitTime + 2_000, 0.8, 0);
        assertEquals(0, e.state().distanceMeters(), 0);
        assertTrue(e.state().incomplete()); // stoppedCount < 3: marked incomplete
    }

    @Test public void straightTunnelDocumentsPositivePriorBias() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        assertEquals(40, e.state().distanceMeters(), 0.001);

        // Straight tunnel 1000m long, traveled at 20 m/s in 50 seconds
        long exitTime = 5_000 + 50_000;
        double exitMeters = 40 + 1_000;
        feed(e, exitTime, exitMeters, 20);
        feed(e, exitTime + 1_000, exitMeters + 20, 20);
        feed(e, exitTime + 2_000, exitMeters + 40, 20);

        double est = e.state().estimatedMeters();
        double totalChord = 1_000 + 40; // 1040m
        assertEquals(totalChord * 1.05, est, 0.01);

        double groundTruth = 1040.0;
        double errorPct = (est - groundTruth) / groundTruth * 100.0;
        // Documents exactly +5.0% overcount on straight tunnel
        assertEquals(5.0, errorPct, 0.01);
    }

    @Test public void longTunnelAcceptsHigherUncertaintyUnderRelativeGate() {
        TripEngine e = engine();
        for (int i = 0; i <= 3; i++) feed(e, 1_000 + i * 1_000, i * 10, 10);
        // Last fix before tunnel has accuracy 35m (urban canyon entrance)
        TripEngine.Fix lastFix = new TripEngine.Fix(5_000, 0, Math.toDegrees(40.0 / 6_371_000), 35, 10, 0.2);
        assertTrue(e.accept(lastFix, 5_000));

        // Tunnel is 1000m long: 0.05 * chord = 50m.
        // u = hypot(35, 20) = 40.31m <= 50m (passes relative gate!)
        long exitTime = 55_000;
        double exitMeters = 40 + 1_000;
        TripEngine.Fix f1 = new TripEngine.Fix(exitTime, 0, Math.toDegrees(exitMeters / 6_371_000), 20, 10, 0.2);
        TripEngine.Fix f2 = new TripEngine.Fix(exitTime + 1_000, 0, Math.toDegrees((exitMeters + 10) / 6_371_000), 20, 10, 0.2);
        TripEngine.Fix f3 = new TripEngine.Fix(exitTime + 2_000, 0, Math.toDegrees((exitMeters + 20) / 6_371_000), 20, 10, 0.2);
        e.accept(f1, exitTime);
        e.accept(f2, exitTime + 1_000);
        assertTrue(e.accept(f3, exitTime + 2_000));
        assertFalse("Long tunnel with 35m entrance accuracy must bridge without incomplete", e.state().incomplete());
        assertTrue("Estimated distance must bridge the tunnel", e.state().estimatedMeters() > 1000);
    }

    @Test public void restorePreservesUncertaintyAndEstimates() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        feed(e, 61_000, 600, 10);
        feed(e, 62_000, 610, 10);
        feed(e, 63_000, 620, 10);
        TripEngine.State snapshot = e.state();
        assertTrue(snapshot.uncertaintyMeters() > 0);

        TripEngine restored = new TripEngine();
        restored.restore(snapshot);
        assertEquals(snapshot.distanceMeters(), restored.state().distanceMeters(), 0.001);
        assertEquals(snapshot.estimatedMeters(), restored.state().estimatedMeters(), 0.001);
        assertEquals(snapshot.uncertaintyMeters(), restored.state().uncertaintyMeters(), 0.001);
    }

    @Test public void uncertaintyAccumulatesMonotonicallyAndResets() {
        TripEngine e = engine();
        feed(e, 1_000, 0, 10);
        feed(e, 61_000, 600, 10);
        feed(e, 62_000, 610, 10);
        feed(e, 63_000, 620, 10);
        double u1 = e.state().uncertaintyMeters();
        assertTrue(u1 > 0);

        // Second gap
        feed(e, 120_000, 1200, 10);
        feed(e, 121_000, 1210, 10);
        feed(e, 122_000, 1220, 10);
        double u2 = e.state().uncertaintyMeters();
        assertTrue(u2 > u1);

        // Reset clears uncertainty
        e.reset(130_000);
        assertEquals(0, e.state().uncertaintyMeters(), 0);
    }

    @Test public void continuousMotionEstimatesDuringGapAndFeedsBridgeRecovery() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 20, 20);
        assertEquals(80, e.state().distanceMeters(), 0.001);
        assertEquals(72, e.state().speedKmh(), 0.001);

        // Gap for 30 seconds in tunnel
        for (int i = 1; i <= 30; i++) {
            long t = 5_000 + i * 1_000;
            e.tick(t);
            e.motion(t, false, Double.NaN);
        }

        assertEquals(600, e.state().gapMeters(), 0.001);
        // A held speed is shown at most FRESH_MS; after 30 s without a fix the speed is unknown.
        assertEquals(-1, e.state().speedKmh(), 0.001);
        // The dead-reckoning estimate is only a bridge prior; displayed distance stays put.
        assertEquals(0.08f, e.state().getDistanceKm(), 0.001f);

        // Exit tunnel: 3 fresh recovery fixes arrive
        feed(e, 36_000, 80 + 600, 20);
        feed(e, 37_000, 80 + 600 + 20, 20);
        feed(e, 38_000, 80 + 600 + 40, 20);

        assertEquals(0, e.state().gapMeters(), 0);
        assertTrue("Distance should commit the estimated bridge", e.state().distanceMeters() >= 700);
        assertEquals(72, e.state().speedKmh(), 0.001);
        assertFalse(e.state().incomplete());
    }

    @Test public void continuousMotionDropsSpeedOnStationaryZupt() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 20, 20);

        // 3 seconds moving in tunnel
        for (int i = 1; i <= 3; i++) {
            long t = 5_000 + i * 1_000;
            e.tick(t);
            e.motion(t, false, Double.NaN);
        }
        assertEquals(60, e.state().gapMeters(), 0.001);
        assertEquals(72, e.state().speedKmh(), 0.001);

        // Traffic jam in tunnel: ZUPT detects stationary
        for (int i = 4; i <= 10; i++) {
            long t = 5_000 + i * 1_000;
            e.tick(t);
            e.motion(t, true, Double.NaN);
        }

        // Speed dropped to 0 and gapMeters frozen at 60m
        assertEquals(0, e.state().speedKmh(), 0.001);
        assertEquals(60, e.state().gapMeters(), 0.001);
    }

    @Test public void motionWithExternalVehicleSpeedOverridesHeldSpeed() {
        TripEngine e = engine();
        for (int i = 0; i <= 4; i++) feed(e, 1_000 + i * 1_000, i * 15, 15);
        assertEquals(54, e.state().speedKmh(), 0.001);

        // Tunnel: OBD reports vehicle speed 25 m/s (90 km/h)
        for (int i = 1; i <= 10; i++) {
            long t = 5_000 + i * 1_000;
            e.tick(t);
            e.motion(t, false, 25.0);
        }

        assertEquals(90, e.state().speedKmh(), 0.001);
        assertEquals(250, e.state().gapMeters(), 0.001);
    }

    @Test public void stationaryZeroVelocityClampingEliminatesSpeedoFlicker() {
        TripEngine e = engine();
        // 1. Initial fix arrives with NaN Doppler speed (common on DHU or indoor)
        TripEngine.Fix f1 = new TripEngine.Fix(1_000, 21.0000, 105.0000, 8.0, Double.NaN, Double.NaN);
        assertTrue(e.accept(f1, 1_000));
        assertEquals(-1f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());

        // 2. Fix 2 arrives with small stationary jitter (0.5m) and NaN speed
        double dLat = 0.5 / 111_000.0;
        TripEngine.Fix f2 = new TripEngine.Fix(2_000, 21.0000 + dLat, 105.0000, 8.0, Double.NaN, Double.NaN);
        assertTrue(e.accept(f2, 2_000));
        // Stationary ZUPT clamps speed to solid 0 KM/H, eliminating the -1 <-> 0 ping-pong!
        assertEquals(0f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
        assertEquals(0, e.state().distanceMeters(), 0.001);
        assertFalse(e.state().incomplete());

        // 3. Heartbeat fires 1 second later: stationary speed must stay steady 0 KM/H
        e.tick(3_000);
        assertEquals(0f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());

        // 4. Tick within 8s grace period keeps speed at 0 KM/H
        e.tick(8_500);
        assertEquals(0f, e.state().speedKmh(), 0.001);
        assertEquals(TripEngine.Signal.TRACKING, e.state().signal());
    }

    @Test public void stillnessZeroesOnlyALowDisplayedSpeed() {
        TripEngine slow = engine();
        feed(slow, 1_000, 0, 1.2);          // 4.3 km/h of Doppler noise at a stop
        slow.stillAtLowSpeed(1_500);
        assertEquals(0f, slow.state().speedKmh(), 0.001f);
        assertEquals("distance is untouched", 0, slow.state().distanceMeters(), 0);

        TripEngine fast = engine();
        feed(fast, 1_000, 0, 20);           // cruising: a quiet IMU must not zero it
        fast.stillAtLowSpeed(1_500);
        assertEquals(72f, fast.state().speedKmh(), 0.001f);
    }

    @Test public void lastOutcomeExplainsRejectedFixes() {
        TripEngine e = new TripEngine();
        e.start(0);
        e.accept(new TripEngine.Fix(1_000, 10, 106, 5, 10, 1), 10_000);
        assertEquals(TripEngine.Outcome.INVALID_TIME, e.lastOutcome());
        e.accept(new TripEngine.Fix(10_000, 10, 106, 400, 10, 1), 10_000);
        assertEquals(TripEngine.Outcome.INVALID_ACCURACY, e.lastOutcome());
        e.accept(new TripEngine.Fix(10_500, 10, 106, 5, 10, 1), 10_500);
        assertEquals(TripEngine.Outcome.ACCEPTED, e.lastOutcome());
        e.accept(new TripEngine.Fix(10_500, 10, 106, 5, 10, 1), 10_600);
        assertEquals(TripEngine.Outcome.DUPLICATE, e.lastOutcome());
        TripEngine stopped = new TripEngine();
        stopped.accept(new TripEngine.Fix(10_000, 10, 106, 5, 10, 1), 10_000);
        assertEquals(TripEngine.Outcome.NOT_RUNNING, stopped.lastOutcome());
    }
}
