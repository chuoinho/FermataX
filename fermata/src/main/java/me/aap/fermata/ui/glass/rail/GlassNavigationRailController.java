package me.aap.fermata.ui.glass.rail;

import static android.view.View.GONE;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.glass.catalog.GlassAddonsCatalogFragment;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.FermataToolBarView;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.NavBarView;

/**
 * Controller managing the lifecycle, mounting, and interactions of the GlassNavigationRailView.
 */
public class GlassNavigationRailController {

    private GlassNavigationRailView glassRail;

    public void init(@NonNull MainActivityDelegate activity) {
        if (!activity.isGlassTheme() || !activity.isCarActivity()) return;
        NavBarView navBar = activity.getNavBar();
        if (navBar != null) navBar.setVisibility(GONE);
        mount(activity);
        refreshAppearance(activity);
    }

    public void mount(@NonNull MainActivityDelegate activity) {
        if (!activity.isGlassTheme() || !activity.isCarActivity()) return;
        if (glassRail != null) return;
        FermataActivity a = activity.getAppActivity();
        ViewGroup root = a.findViewById(R.id.main_activity);
        if (root != null) {
            glassRail = new GlassNavigationRailView(a.getContext(), activity);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            glassRail.setLayoutParams(lp);
            glassRail.setElevation(GlassDimensions.dpToPx(a.getContext(), 24));
            glassRail.setTranslationZ(GlassDimensions.dpToPx(a.getContext(), 24));
            root.addView(glassRail);
        }
    }

    @Nullable
    public GlassNavigationRailView getRail() {
        return glassRail;
    }

    public boolean onBackPressed() {
        return glassRail != null && glassRail.onBackPressed();
    }

    public void setBarsHidden(boolean barsHidden) {
        if (glassRail != null) glassRail.setFullscreenHidden(barsHidden);
    }

    public void refreshAppearance(@NonNull MainActivityDelegate activity) {
        me.aap.fermata.ui.glass.theme.GlassThemeManager.applyAppearance(activity);
    }
}
