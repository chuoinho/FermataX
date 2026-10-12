package me.aap.fermata.ui.glass.weather;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free, keyless global weather client powered by Open-Meteo REST API.
 */
public class GlassOpenMeteoClient {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private static final long CACHE_DURATION_MS = 20 * 60 * 1000L; // 20 minutes

    public interface ForecastCallback {
        void onSuccess(@NonNull HourlyForecast forecast);
        void onError(@NonNull Throwable error);
    }

    public static class HourlyPoint {
        private final String time;
        private final float temp;
        private final int weatherCode;
        private final boolean isDay;

        public HourlyPoint(String time, float temp, int weatherCode, boolean isDay) {
            this.time = time;
            this.temp = temp;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
        }

        public String getTime() {
            return time;
        }

        public float getTemp() {
            return temp;
        }

        public int getWeatherCode() {
            return weatherCode;
        }

        public boolean isDay() {
            return isDay;
        }
    }

    public static class HourlyForecast {
        private final float currentTemp;
        private final int currentWeatherCode;
        private final boolean isDay;
        private final float highTemp;
        private final float lowTemp;
        private final List<HourlyPoint> hourlyPoints;
        private final long timestamp;

        public HourlyForecast(float currentTemp, int currentWeatherCode, boolean isDay,
                              float highTemp, float lowTemp, List<HourlyPoint> hourlyPoints) {
            this.currentTemp = currentTemp;
            this.currentWeatherCode = currentWeatherCode;
            this.isDay = isDay;
            this.highTemp = highTemp;
            this.lowTemp = lowTemp;
            this.hourlyPoints = hourlyPoints != null ? hourlyPoints : Collections.emptyList();
            this.timestamp = System.currentTimeMillis();
        }

        public float getCurrentTemp() {
            return currentTemp;
        }

        public int getCurrentWeatherCode() {
            return currentWeatherCode;
        }

        public boolean isDay() {
            return isDay;
        }

        public float getHighTemp() {
            return highTemp;
        }

        public float getLowTemp() {
            return lowTemp;
        }

        public List<HourlyPoint> getHourlyPoints() {
            return hourlyPoints;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION_MS;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HourlyForecast cachedForecast = null;
    private double lastLat = 0;
    private double lastLon = 0;

    public void fetchForecast(double lat, double lon, boolean isMetric, @NonNull ForecastCallback callback) {
        if (cachedForecast != null && !cachedForecast.isExpired()
                && Math.abs(lastLat - lat) < 0.05 && Math.abs(lastLon - lon) < 0.05) {
            callback.onSuccess(cachedForecast);
            return;
        }

        executor.execute(() -> {
            try {
                String tempUnit = isMetric ? "celsius" : "fahrenheit";
                String windUnit = isMetric ? "kmh" : "mph";
                String urlStr = String.format(Locale.US,
                        "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day" +
                                "&hourly=temperature_2m,weather_code,is_day" +
                                "&daily=temperature_2m_max,temperature_2m_min" +
                                "&temperature_unit=%s&wind_speed_unit=%s&timezone=auto&forecast_days=2",
                        BASE_URL, lat, lon, tempUnit, windUnit);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    HourlyForecast forecast = parseForecastJson(sb.toString());
                    this.cachedForecast = forecast;
                    this.lastLat = lat;
                    this.lastLon = lon;
                    mainHandler.post(() -> callback.onSuccess(forecast));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("HTTP Error: " + responseCode)));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static HourlyForecast parseForecastJson(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);

        JSONObject current = root.optJSONObject("current");
        float currentTemp = current != null ? (float) current.optDouble("temperature_2m", 20.0) : 20.0f;
        int weatherCode = current != null ? current.optInt("weather_code", 0) : 0;
        boolean isDay = current == null || current.optInt("is_day", 1) == 1;

        float highTemp = currentTemp;
        float lowTemp = currentTemp;
        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
            if (maxArr != null && maxArr.length() > 0) {
                highTemp = (float) maxArr.optDouble(0, currentTemp);
            }
            if (minArr != null && minArr.length() > 0) {
                lowTemp = (float) minArr.optDouble(0, currentTemp);
            }
        }

        List<HourlyPoint> points = new ArrayList<>();
        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.optJSONArray("time");
            JSONArray temps = hourly.optJSONArray("temperature_2m");
            JSONArray codes = hourly.optJSONArray("weather_code");
            JSONArray days = hourly.optJSONArray("is_day");

            if (times != null && temps != null && codes != null) {
                int count = Math.min(24, times.length());
                for (int i = 0; i < count; i++) {
                    String rawTime = times.optString(i, "");
                    // extract HH:mm from "2026-10-13T14:00"
                    String formattedTime = rawTime.contains("T") ? rawTime.substring(rawTime.indexOf("T") + 1) : rawTime;
                    float t = (float) temps.optDouble(i, 20.0);
                    int c = codes.optInt(i, 0);
                    boolean d = days == null || days.optInt(i, 1) == 1;
                    points.add(new HourlyPoint(formattedTime, t, c, d));
                }
            }
        }

        return new HourlyForecast(currentTemp, weatherCode, isDay, highTemp, lowTemp, points);
    }

    public static String getWeatherConditionDescription(int weatherCode) {
        switch (weatherCode) {
            case 0:
                return "Clear";
            case 1:
                return "Mainly Clear";
            case 2:
                return "Partly Cloudy";
            case 3:
                return "Overcast";
            case 45:
            case 48:
                return "Fog";
            case 51:
            case 53:
            case 55:
                return "Drizzle";
            case 61:
            case 63:
            case 65:
                return "Rain";
            case 71:
            case 73:
            case 75:
                return "Snow";
            case 80:
            case 81:
            case 82:
                return "Rain Showers";
            case 95:
            case 96:
            case 99:
                return "Thunderstorm";
            default:
                return "Fair";
        }
    }
}
