package me.aap.fermata.ui.glass.nav;

import static org.junit.Assert.*;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
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
    public void mapsDrivingStatusNotificationIsNotNavigation() {
        // Real Maps foreground notification while driving without a route: category=navigation, no actions
        Bundle extras = new Bundle();
        extras.putString("android.title", "P. Trường Lâm");
        Notification n = new Notification();
        n.category = Notification.CATEGORY_NAVIGATION;

        assertFalse(NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, n).isActive());
    }

    @Test
    public void navigationEndingInSameNotificationClearsTheTurn() {
        String pkg = "com.google.android.apps.maps";
        Notification nav = new Notification.Builder(RuntimeEnvironment.getApplication(), "c")
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setContentTitle("In 300m Turn Right onto Broadway").build();
        Notification status = new Notification.Builder(RuntimeEnvironment.getApplication(), "c")
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setContentTitle("P. Trường Lâm").build();

        AutomotiveNavNotificationService.handlePosted(
                new StatusBarNotification(pkg, pkg, 1, null, 1000, 0, 0, nav, Process.myUserHandle(), 0L));
        assertTrue(AutomotiveNavNotificationService.getCurrentInfo().isActive());
        AutomotiveNavNotificationService.handlePosted(
                new StatusBarNotification(pkg, pkg, 1, null, 1000, 0, 0, status, Process.myUserHandle(), 0L));
        assertFalse(AutomotiveNavNotificationService.getCurrentInfo().isActive());
    }

    @Test
    public void testGlassNavWeatherViewTransitions() {
        GlassNavWeatherView view = new GlassNavWeatherView(RuntimeEnvironment.getApplication());
        view.bindWeather(new GlassOpenMeteoClient.HourlyForecast(
                22f, 1, true, 26f, 18f,
                Collections.singletonList(new GlassOpenMeteoClient.HourlyPoint("15:00", 22f, 1, true))
        ), true);
        assertNull("no nav: weather only, no way to switch", button(view, "WEATHER"));
        assertNull(button(view, "NAVIGATION"));

        view.bindNavTurn(activeTurn("In 300m Turn Left on Oxford St"));
        assertNull(button(view, "NAVIGATION"));
        button(view, "WEATHER").performClick();
        assertNull(button(view, "WEATHER"));

        view.bindNavTurn(activeTurn("In 100m Turn Left on Oxford St"));
        assertNull("turn updates keep the user's choice", button(view, "WEATHER"));
        button(view, "NAVIGATION").performClick();
        assertNull(button(view, "NAVIGATION"));

        button(view, "WEATHER").performClick();
        view.bindNavTurn(NavTurnInfo.inactive());
        assertNull("nav ended: weather only", button(view, "WEATHER"));
        assertNull(button(view, "NAVIGATION"));

        view.bindNavTurn(activeTurn("In 300m Turn Right onto Broadway"));
        assertNotNull("next route shows nav again", button(view, "WEATHER"));
    }

    @Test
    public void removingAnotherNotificationOfTheNavAppKeepsTheTurn() {
        Notification n = new Notification.Builder(RuntimeEnvironment.getApplication(), "c")
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setContentTitle("In 300m Turn Right onto Broadway").setContentText("4.2 km · 12 min").build();
        String pkg = "com.google.android.apps.maps";
        StatusBarNotification nav = new StatusBarNotification(pkg, pkg, 1, null, 1000, 0, 0, n, Process.myUserHandle(), 0L);
        StatusBarNotification other = new StatusBarNotification(pkg, pkg, 2, null, 1000, 0, 0, n, Process.myUserHandle(), 0L);

        AutomotiveNavNotificationService.handlePosted(nav);
        assertTrue(AutomotiveNavNotificationService.getCurrentInfo().isActive());
        AutomotiveNavNotificationService.handleRemoved(other.getKey());
        assertTrue("another Maps notification must not clear the turn", AutomotiveNavNotificationService.getCurrentInfo().isActive());
        AutomotiveNavNotificationService.handleRemoved(nav.getKey());
        assertFalse(AutomotiveNavNotificationService.getCurrentInfo().isActive());
    }

    private static NavTurnInfo activeTurn(String title) {
        Bundle extras = new Bundle();
        extras.putString("android.title", title);
        return NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, null);
    }

    // findViewsWithText only walks visible views, so null means the button is not on screen
    private static View button(View root, String text) {
        ArrayList<View> found = new ArrayList<>();
        root.findViewsWithText(found, text, View.FIND_VIEWS_WITH_TEXT);
        for (View v : found) if (v.isClickable()) return v;
        return null;
    }
}
