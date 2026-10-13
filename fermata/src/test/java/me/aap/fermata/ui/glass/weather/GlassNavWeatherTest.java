package me.aap.fermata.ui.glass.weather;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.ui.glass.dashboard.GlassNavWeatherView;

@RunWith(RobolectricTestRunner.class)
public class GlassNavWeatherTest {

    @Test
    public void testHourlyForecastFiltersFromCurrentHour() throws Exception {
        String json = "{\n" +
                "  \"current\": {\"temperature_2m\": 28.5, \"weather_code\": 0, \"is_day\": 1, \"time\": \"2026-10-13T14:00\"},\n" +
                "  \"daily\": {\"temperature_2m_max\": [32.0], \"temperature_2m_min\": [24.0]},\n" +
                "  \"hourly\": {\n" +
                "    \"time\": [\"2026-10-13T12:00\", \"2026-10-13T13:00\", \"2026-10-13T14:00\", \"2026-10-13T15:00\", \"2026-10-13T16:00\"],\n" +
                "    \"temperature_2m\": [26.0, 27.5, 28.5, 29.0, 28.0],\n" +
                "    \"weather_code\": [0, 0, 0, 1, 1],\n" +
                "    \"is_day\": [1, 1, 1, 1, 1]\n" +
                "  }\n" +
                "}";

        GlassOpenMeteoClient.HourlyForecast forecast = GlassOpenMeteoClient.parseForecastJson(json);
        assertNotNull(forecast);
        assertEquals(28.5f, forecast.getCurrentTemp(), 0.1f);
        assertEquals(32.0f, forecast.getHighTemp(), 0.1f);
        assertEquals(24.0f, forecast.getLowTemp(), 0.1f);

        // Current time in JSON is 14:00, so first hourly point MUST be 14:00, NOT 12:00!
        assertFalse(forecast.getHourlyPoints().isEmpty());
        assertEquals("14:00", forecast.getHourlyPoints().get(0).getTime());
        assertEquals(28.5f, forecast.getHourlyPoints().get(0).getTemp(), 0.1f);
        assertEquals("15:00", forecast.getHourlyPoints().get(1).getTime());
    }

    @Test
    public void testGlassNavWeatherViewStates() {
        GlassNavWeatherView view = new GlassNavWeatherView(RuntimeEnvironment.getApplication());
        assertNotNull(view);

        // Permission needed state
        boolean[] actionRan = new boolean[]{false};
        view.showPermissionNeeded("Please grant location permission", () -> actionRan[0] = true);

        // Locating state
        view.showLocating("Fixing satellites");

        // Error state
        boolean[] retryRan = new boolean[]{false};
        view.showError("Failed to fetch", () -> retryRan[0] = true);
    }
}
