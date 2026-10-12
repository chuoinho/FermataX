package me.aap.fermata.ui.glass.weather;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GlassOpenMeteoClientTest {

    @Test
    public void testJsonParsing() throws Exception {
        String json = "{\n" +
                "  \"current\": {\n" +
                "    \"temperature_2m\": 24.5,\n" +
                "    \"weather_code\": 1,\n" +
                "    \"is_day\": 1\n" +
                "  },\n" +
                "  \"daily\": {\n" +
                "    \"temperature_2m_max\": [28.0],\n" +
                "    \"temperature_2m_min\": [20.0]\n" +
                "  },\n" +
                "  \"hourly\": {\n" +
                "    \"time\": [\"2026-10-13T01:00\", \"2026-10-13T02:00\"],\n" +
                "    \"temperature_2m\": [23.0, 22.5],\n" +
                "    \"weather_code\": [1, 2],\n" +
                "    \"is_day\": [0, 0]\n" +
                "  }\n" +
                "}";

        GlassOpenMeteoClient.HourlyForecast forecast = GlassOpenMeteoClient.parseForecastJson(json);
        assertNotNull(forecast);
        assertEquals(24.5f, forecast.getCurrentTemp(), 0.01f);
        assertEquals(1, forecast.getCurrentWeatherCode());
        assertTrue(forecast.isDay());
        assertEquals(28.0f, forecast.getHighTemp(), 0.01f);
        assertEquals(20.0f, forecast.getLowTemp(), 0.01f);
        assertEquals(2, forecast.getHourlyPoints().size());
        assertEquals("01:00", forecast.getHourlyPoints().get(0).getTime());
        assertEquals(23.0f, forecast.getHourlyPoints().get(0).getTemp(), 0.01f);
    }

    @Test
    public void testWeatherConditionDescriptions() {
        assertEquals("Clear", GlassOpenMeteoClient.getWeatherConditionDescription(0));
        assertEquals("Mainly Clear", GlassOpenMeteoClient.getWeatherConditionDescription(1));
        assertEquals("Partly Cloudy", GlassOpenMeteoClient.getWeatherConditionDescription(2));
        assertEquals("Overcast", GlassOpenMeteoClient.getWeatherConditionDescription(3));
        assertEquals("Rain", GlassOpenMeteoClient.getWeatherConditionDescription(61));
        assertEquals("Thunderstorm", GlassOpenMeteoClient.getWeatherConditionDescription(95));
    }
}
