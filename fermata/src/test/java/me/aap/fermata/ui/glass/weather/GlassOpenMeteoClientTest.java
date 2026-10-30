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

    private static String hazardJson(String units, String current, String hourlyGusts, String hourlyVis,
                                     String hourlyCodes, String hourlyTemps) {
        return "{\"current_units\":" + units + ",\"hourly_units\":" + units + "," +
                "\"current\":{\"time\":\"2026-10-13T01:00\"," + current + "}," +
                "\"hourly\":{\"time\":[\"2026-10-13T01:00\",\"2026-10-13T02:00\",\"2026-10-13T03:00\"," +
                "\"2026-10-13T04:00\",\"2026-10-13T05:00\"]," +
                "\"temperature_2m\":" + hourlyTemps + ",\"weather_code\":" + hourlyCodes +
                (hourlyGusts == null ? "" : ",\"wind_gusts_10m\":" + hourlyGusts) +
                (hourlyVis == null ? "" : ",\"visibility\":" + hourlyVis) + "}}";
    }

    @Test
    public void urlRequestsGustsAndVisibility() {
        String url = GlassOpenMeteoClient.buildUrl(52.52, 13.41, true);
        assertTrue(url.contains("current=temperature_2m,weather_code,is_day,wind_gusts_10m,visibility"));
        assertTrue(url.contains("hourly=temperature_2m,weather_code,is_day,wind_gusts_10m,visibility"));
    }

    @Test
    public void parsesWithoutHazardFieldsAsNoHazard() throws Exception {
        GlassOpenMeteoClient.HourlyForecast f = GlassOpenMeteoClient.parseForecastJson(hazardJson(
                "{}", "\"temperature_2m\":0.5,\"weather_code\":61", null, null,
                "[1,1,1,1,1]", "[10,10,10,10,10]"));
        // Current sample alone is near freezing with rain.
        assertEquals(RoadHazardPolicy.Type.ICE, f.getHazard().getType());

        f = GlassOpenMeteoClient.parseForecastJson("{\"current\":{\"temperature_2m\":20}}");
        assertEquals(RoadHazardPolicy.Type.NONE, f.getHazard().getType());
    }

    @Test
    public void hazardLooksAtNextThreeHoursInReportedUnits() throws Exception {
        String imperial = "{\"temperature_2m\":\"°F\",\"wind_gusts_10m\":\"mp/h\",\"visibility\":\"ft\"}";
        // 45 mp/h gusts at 03:00 -> ~72 km/h.
        GlassOpenMeteoClient.HourlyForecast f = GlassOpenMeteoClient.parseForecastJson(hazardJson(imperial,
                "\"temperature_2m\":60,\"weather_code\":1,\"wind_gusts_10m\":10,\"visibility\":30000",
                "[10,10,45,10,10]", "[30000,30000,30000,30000,30000]", "[1,1,1,1,1]", "[60,60,60,60,60]"));
        assertEquals(RoadHazardPolicy.Type.WIND, f.getHazard().getType());
        assertEquals(72.4f, f.getHazard().getGustKmh(), 0.1f);

        // 34°F with rain is ice risk; 1000 ft visibility (305 m) is fog, ice still wins.
        f = GlassOpenMeteoClient.parseForecastJson(hazardJson(imperial,
                "\"temperature_2m\":40,\"weather_code\":1",
                null, "[30000,1000,30000,30000,30000]", "[1,1,61,1,1]", "[40,40,34,40,40]"));
        assertEquals(RoadHazardPolicy.Type.ICE, f.getHazard().getType());

        // Fog only at 05:00 is beyond the 3 hour window.
        f = GlassOpenMeteoClient.parseForecastJson(hazardJson("{}",
                "\"temperature_2m\":15,\"weather_code\":1",
                null, null, "[1,1,1,1,45]", "[15,15,15,15,15]"));
        assertEquals(RoadHazardPolicy.Type.NONE, f.getHazard().getType());
    }
}
