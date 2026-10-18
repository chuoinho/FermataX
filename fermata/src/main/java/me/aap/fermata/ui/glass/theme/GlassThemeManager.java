package me.aap.fermata.ui.glass.theme;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.glass.catalog.GlassAddonsCatalogFragment;
import me.aap.fermata.ui.glass.rail.GlassNavigationRailController;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.FermataToolBarView;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Single authority for synchronizing Glass appearance across the Window, status bar,
 * navigation bar, activity root, topbar, rail, and active routes.
 */
public final class GlassThemeManager {

    private GlassThemeManager() {
    }

    public static void applyAppearance(@NonNull MainActivityDelegate activity) {
        if (!activity.isGlassTheme()) return;

        Context ctx = activity.getContext();
        boolean isNight = activity.getPrefs().isGlassNightMode();

        // 1. Context theme
        MainActivityDelegate.setTheme(ctx, activity.isCarActivity());

        // 2. Activity Root view background
        View root = activity.getAppActivity().findViewById(R.id.main_activity);
        if (root != null) {
            root.setBackgroundColor(isNight ? 0xFF07090D : 0xFFF8FAFC);
        }

        // 3. Window decor, background, and system status/nav bars
        Window window = activity.getAppActivity().getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(isNight ? 0xFF07090D : 0xFFF8FAFC));
            window.setStatusBarColor(isNight ? 0xFF0E141D : 0xFFE2E8F0);
            window.setNavigationBarColor(isNight ? 0xFF0E141D : 0xFFFFFFFF);

            View decor = window.getDecorView();
            if (decor != null) {
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
                if (controller != null) {
                    controller.setAppearanceLightStatusBars(!isNight);
                    controller.setAppearanceLightNavigationBars(!isNight);
                }
            }
        }

        // 4. Toolbar
        if (activity.getToolBar() instanceof FermataToolBarView ftb) {
            ftb.applyGlassThemeIfNeeded();
        }

        // 5. Navigation rail
        GlassNavigationRailController rail = activity.getGlassRailController();
        if (rail != null && rail.getRail() != null) {
            rail.getRail().onAppearanceChanged();
        }

        // 6. Active fragment appearance
        ActivityFragment frag = activity.getActiveFragment();
        if (frag instanceof DashboardFragment dashboard) {
            dashboard.reload();
        } else if (frag instanceof MediaLibFragment mediaLib) {
            mediaLib.refreshAppearance();
        } else if (frag instanceof SettingsFragment settings) {
            settings.refreshAppearance();
        } else if (frag instanceof GlassAddonsCatalogFragment catalog) {
            catalog.refreshAppearance();
        } else if (frag instanceof me.aap.utils.ui.fragment.GenericFragment gf) {
            View fv = gf.getView();
            if (fv != null) {
                android.graphics.drawable.Drawable glass = GlassDrawables.createGlassCardDrawable(ctx, 20f, isNight);
                if (glass != null) {
                    fv.setBackground(glass);
                }
                refreshChildSettingsViews(fv, isNight);
            }
        }

        // 7. Fullscreen video control panel
        ControlPanelView cp = activity.getControlPanel();
        if (cp != null) {
            cp.applyGlassStyling();
        }

        // 8. Active OverlayMenu dialog restyling
        try {
            me.aap.utils.ui.menu.OverlayMenu menu = activity.getActiveMenu();
            if (menu instanceof android.view.ViewGroup mv && mv.getVisibility() == View.VISIBLE) {
                if (mv.getChildCount() > 0) {
                    View child = mv.getChildAt(0);
                    me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleGlassDialog(child, ctx, isNight);
                }
                me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleGlassDialog(mv, ctx, isNight);
            }
        } catch (Throwable ignored) {}

        // 9. Active DialogFragment restyling
        try {
            if (activity.getAppActivity() instanceof androidx.fragment.app.FragmentActivity fa) {
                for (androidx.fragment.app.Fragment f : fa.getSupportFragmentManager().getFragments()) {
                    if (f instanceof androidx.fragment.app.DialogFragment df && df.getDialog() != null && df.getDialog().isShowing()) {
                        me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.refreshAlertDialog(df.getDialog(), ctx, isNight);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 10. Broadcast event to all registered controllers (e.g. PhonePlaybackCarHeaderController, PhoneSupportBannerController, web listeners)
        activity.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
    }

    private static void refreshChildSettingsViews(View view, boolean isNight) {
        if (view instanceof androidx.recyclerview.widget.RecyclerView rv) {
            me.aap.fermata.ui.glass.settings.GlassSettingsDecorator.apply(rv, isNight);
        } else if (view instanceof android.view.ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                refreshChildSettingsViews(vg.getChildAt(i), isNight);
            }
        }
    }
}
