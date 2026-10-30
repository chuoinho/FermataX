package me.aap.fermata.ui.glass.weather;

import static org.junit.Assert.*;

import org.junit.Test;

import me.aap.fermata.ui.glass.weather.RoadHazardPolicy.Type;

public class RoadHazardPolicyTest {
    private static final float NA = Float.NaN;

    private static Type type(float tempC, int code, float gustKmh, float visM) {
        return RoadHazardPolicy.of(tempC, code, gustKmh, visM).getType();
    }

    @Test
    public void iceNeedsNearFreezingTemperatureAndWetWeather() {
        assertEquals(Type.ICE, type(-3f, 61, NA, NA));
        assertEquals(Type.ICE, type(3f, 71, NA, NA));
        assertEquals(Type.ICE, type(0f, 56, NA, NA));
        assertEquals(Type.NONE, type(3.1f, 61, NA, NA));
        assertEquals(Type.NONE, type(-3.1f, 71, NA, NA));
        assertEquals(Type.NONE, type(0f, 0, NA, NA));
    }

    @Test
    public void fogFromCodeOrLowVisibility() {
        assertEquals(Type.FOG, type(10f, 45, NA, NA));
        assertEquals(Type.FOG, type(10f, 48, NA, NA));
        assertEquals(Type.FOG, type(10f, 3, NA, 499f));
        assertEquals(Type.NONE, type(10f, 3, NA, 500f));
    }

    @Test
    public void heavyPrecipitationCodes() {
        for (int code : new int[]{65, 67, 75, 82, 86, 95, 96, 99}) {
            assertEquals("code " + code, Type.HEAVY_PRECIP, type(15f, code, NA, NA));
        }
        assertEquals(Type.NONE, type(15f, 63, NA, NA));
    }

    @Test
    public void windFromGustThreshold() {
        RoadHazardPolicy.Hazard h = RoadHazardPolicy.of(15f, 1, 68f, NA);
        assertEquals(Type.WIND, h.getType());
        assertEquals(68f, h.getGustKmh(), 0.01f);
        assertEquals(Type.NONE, type(15f, 1, 59.9f, NA));
    }

    @Test
    public void mostSevereWins() {
        assertEquals(Type.ICE, type(0f, 65, 80f, 100f));
        assertEquals(Type.FOG, type(10f, 45, 80f, NA));
        assertEquals(Type.HEAVY_PRECIP, type(10f, 82, 80f, NA));

        RoadHazardPolicy.Hazard wind = RoadHazardPolicy.of(10f, 1, 70f, NA);
        RoadHazardPolicy.Hazard fog = RoadHazardPolicy.of(10f, 45, NA, NA);
        assertSame(fog, RoadHazardPolicy.worst(wind, fog));
        assertSame(fog, RoadHazardPolicy.worst(fog, wind));
        RoadHazardPolicy.Hazard stronger = RoadHazardPolicy.of(10f, 1, 90f, NA);
        assertSame(stronger, RoadHazardPolicy.worst(wind, stronger));
    }

    @Test
    public void missingDataIsNoHazard() {
        assertEquals(Type.NONE, type(NA, -1, NA, NA));
        assertEquals(Type.NONE, type(NA, 61, NA, NA));
    }

    @Test
    public void convertsImperialUnits() {
        assertEquals(0f, RoadHazardPolicy.toCelsius(32f, "°F"), 0.01f);
        assertEquals(5f, RoadHazardPolicy.toCelsius(5f, "°C"), 0.01f);
        assertEquals(64.37f, RoadHazardPolicy.toKmh(40f, "mp/h"), 0.01f);
        assertEquals(40f, RoadHazardPolicy.toKmh(40f, "km/h"), 0.01f);
        assertEquals(304.8f, RoadHazardPolicy.toMeters(1000f, "ft"), 0.01f);
        assertEquals(1000f, RoadHazardPolicy.toMeters(1000f, "m"), 0.01f);
    }
}
