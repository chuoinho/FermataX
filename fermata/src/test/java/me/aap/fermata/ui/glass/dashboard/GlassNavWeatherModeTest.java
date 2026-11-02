package me.aap.fermata.ui.glass.dashboard;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.nav.NavTurnInfo;

@RunWith(RobolectricTestRunner.class)
public class GlassNavWeatherModeTest {
    private Context ctx;

    @Before
    public void clearSavedMode() {
        ctx = RuntimeEnvironment.getApplication();
        GlassNavWeatherView.clearModeForTesting(ctx);
    }

    private static NavTurnInfo turn() {
        Bundle extras = new Bundle();
        extras.putString("android.title", "In 300 m turn left");
        extras.putString("android.subText", "11:15");
        return NavTurnInfo.fromNotification("com.google.android.apps.maps", extras, null);
    }

    private static View findByDescription(View v, String desc) {
        if (desc.contentEquals(String.valueOf(v.getContentDescription()))) return v;
        if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findByDescription(g.getChildAt(i), desc);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View showWeatherButton(GlassNavWeatherView view) {
        return findByDescription(view, ctx.getString(R.string.glass_card_show_weather_desc));
    }

    private View showNavButton(GlassNavWeatherView view) {
        return findByDescription(view, ctx.getString(R.string.glass_card_show_nav_desc));
    }

    @Test
    public void noNavigationShowsWeatherWithoutToggle() {
        GlassNavWeatherView view = new GlassNavWeatherView(ctx);
        view.bindNavTurn(NavTurnInfo.inactive());
        assertFalse(view.isShowingNavigation());
        assertEquals(View.GONE, showNavButton(view).getVisibility());
        assertEquals(View.GONE, showWeatherButton(view).getVisibility());
    }

    @Test
    public void navigationShowsByDefaultWithToggle() {
        GlassNavWeatherView view = new GlassNavWeatherView(ctx);
        view.bindNavTurn(turn());
        assertTrue(view.isShowingNavigation());
        assertEquals(View.VISIBLE, showWeatherButton(view).getVisibility());
    }

    @Test
    public void manualChoiceSurvivesTurnUpdatesAndNewNavigation() {
        GlassNavWeatherView view = new GlassNavWeatherView(ctx);
        view.bindNavTurn(turn());
        showWeatherButton(view).performClick();
        assertFalse(view.isShowingNavigation());
        assertEquals(View.VISIBLE, showNavButton(view).getVisibility());

        view.bindNavTurn(turn());
        assertFalse("a turn update must not switch back", view.isShowingNavigation());

        view.bindNavTurn(NavTurnInfo.inactive());
        assertEquals(View.GONE, showNavButton(view).getVisibility());
        view.bindNavTurn(turn());
        assertFalse("a new navigation keeps the chosen mode", view.isShowingNavigation());

        showNavButton(view).performClick();
        assertTrue(view.isShowingNavigation());
    }

    @Test
    public void choiceIsRememberedByANewCard() {
        GlassNavWeatherView view = new GlassNavWeatherView(ctx);
        view.bindNavTurn(turn());
        showWeatherButton(view).performClick();

        GlassNavWeatherView recreated = new GlassNavWeatherView(ctx);
        recreated.bindNavTurn(turn());
        assertFalse(recreated.isShowingNavigation());
    }
}
