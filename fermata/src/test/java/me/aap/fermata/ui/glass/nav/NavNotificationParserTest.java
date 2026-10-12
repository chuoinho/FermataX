package me.aap.fermata.ui.glass.nav;

import static org.junit.Assert.*;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import me.aap.fermata.ui.glass.dashboard.GlassNavWeatherView;
import me.aap.fermata.ui.glass.weather.GlassOpenMeteoClient;

@RunWith(RobolectricTestRunner.class)
public class NavNotificationParserTest {

    @Test
    public void testNavigationNotificationExtractionWithoutLanguageHardcoding() {
        Bundle extras = new Bundle();
        extras.putString("android.title", "In 300m Turn Right onto Broadway");
        extras.putString("android.subText", "11:15");
        extras.putString("android.text", "4.2 km · 12 min");

        NavTurnInfo info = NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, null);
        assertNotNull(info);
        assertTrue(info.isActive());
        assertEquals("11:15", info.getEta());
        assertTrue(info.getInstruction().contains("Broadway"));
        assertEquals(NavTurnInfo.TurnDirection.TURN_RIGHT, info.getDirection());
    }

    @Test
    public void testTurnDirectionInference() {
        assertEquals(NavTurnInfo.TurnDirection.TURN_LEFT,
                NavTurnInfo.inferDirection("Turn left on Main St"));
        assertEquals(NavTurnInfo.TurnDirection.TURN_RIGHT,
                NavTurnInfo.inferDirection("Rẽ phải vào đường Lê Lợi"));
        assertEquals(NavTurnInfo.TurnDirection.U_TURN,
                NavTurnInfo.inferDirection("Make a U-turn at 5th Ave"));
        assertEquals(NavTurnInfo.TurnDirection.ROUNDABOUT,
                NavTurnInfo.inferDirection("At the roundabout, take the 2nd exit"));
        assertEquals(NavTurnInfo.TurnDirection.STRAIGHT,
                NavTurnInfo.inferDirection("Continue straight for 5 km"));
    }

    @Test
    public void testExitActionExtraction() {
        Intent exitIntent = new Intent("EXIT_NAV");
        PendingIntent pi = PendingIntent.getBroadcast(
                RuntimeEnvironment.getApplication(), 0, exitIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Action action = new Notification.Action.Builder(
                0, "Exit", pi).build();

        Notification n = new Notification();
        n.actions = new Notification.Action[]{action};

        Bundle extras = new Bundle();
        extras.putString("android.title", "Navigate to Airport");
        extras.putString("android.subText", "14:30");

        NavTurnInfo info = NavTurnInfo.fromNotification("com.waze", extras, n);
        assertNotNull(info);
        assertTrue(info.hasExitAction());
        assertEquals(pi, info.getExitIntent());
    }

    @Test
    public void testGlassNavWeatherViewTransitions() {
        GlassNavWeatherView view = new GlassNavWeatherView(RuntimeEnvironment.getApplication());
        assertNotNull(view);

        // Bind Weather
        GlassOpenMeteoClient.HourlyForecast forecast = new GlassOpenMeteoClient.HourlyForecast(
                22f, 1, true, 26f, 18f,
                Collections.singletonList(new GlassOpenMeteoClient.HourlyPoint("15:00", 22f, 1, true))
        );
        view.bindWeather(forecast, true);

        // Bind Active Nav Turn
        Bundle extras = new Bundle();
        extras.putString("android.title", "Turn Left on Oxford St");
        NavTurnInfo activeNav = NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, null);
        view.bindNavTurn(activeNav);

        // Bind Inactive Nav Turn -> falls back to weather
        view.bindNavTurn(NavTurnInfo.inactive());
    }
}
