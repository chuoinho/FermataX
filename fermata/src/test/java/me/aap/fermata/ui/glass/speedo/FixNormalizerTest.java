package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FixNormalizerTest {
    private static TripEngine.Fix fix(double acc, double speed, double speedAcc) {
        return new TripEngine.Fix(1_000, 10, 106, acc, speed, speedAcc);
    }

    @Test public void missingPlatformValuesBecomeNaN() {
        TripEngine.Fix f = FixNormalizer.of(5_000, 10, 106, false, 3, false, 7, false, 1);
        assertTrue(Double.isNaN(f.accuracy()));
        assertTrue(Double.isNaN(f.speedMps()));
        assertTrue(Double.isNaN(f.speedAccuracy()));
        assertFalse(FixNormalizer.valid(f, 5_000));
    }

    @Test public void dopplerIsKeptUnlessItsSigmaIsClearlyWorse() {
        assertEquals(3, FixNormalizer.trustedSpeed(fix(5, 3, 1.4)), 0);   // old rule rejected > 1.05
        assertEquals(10, FixNormalizer.trustedSpeed(fix(5, 10, 4.0)), 0); // old rule rejected > 3.5
        assertTrue(Double.isNaN(FixNormalizer.trustedSpeed(fix(5, 10, 6.0))));
        assertTrue(Double.isNaN(FixNormalizer.trustedSpeed(fix(5, 80, 0.5))));
        assertTrue(Double.isNaN(FixNormalizer.trustedSpeed(fix(5, Double.NaN, 0.5))));
        assertEquals(10, FixNormalizer.trustedSpeed(fix(5, 10, Double.NaN)), 0);
    }

    @Test public void confidentStopsAreZero() {
        assertEquals(0, FixNormalizer.trustedSpeed(fix(5, 0.1, Double.NaN)), 0);
        assertEquals(0, FixNormalizer.trustedSpeed(fix(5, 1.2, 1.5)), 0);
    }

    @Test public void onlyDopplerOrTrustedPositionsAnchor() {
        assertTrue(FixNormalizer.anchors(fix(150, 10, 0.5), 10));
        assertTrue(FixNormalizer.anchors(fix(15, Double.NaN, Double.NaN), Double.NaN));
        assertFalse(FixNormalizer.anchors(fix(150, Double.NaN, Double.NaN), Double.NaN));
        assertFalse(FixNormalizer.anchors(fix(30, Double.NaN, Double.NaN), Double.NaN));
    }
}
