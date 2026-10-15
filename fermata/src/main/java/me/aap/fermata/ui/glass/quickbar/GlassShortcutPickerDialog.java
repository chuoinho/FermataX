package me.aap.fermata.ui.glass.quickbar;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.glass.dialog.GlassDialogBuilder;

/**
 * Picker dialog allowing users to choose an addon or library destination for a quick bar slot.
 */
public class GlassShortcutPickerDialog {

    public interface OnShortcutSelectedListener {
        void onShortcutSelected(@NonNull GlassQuickShortcut shortcut);
    }

    public static List<GlassQuickShortcut> getAvailableShortcuts(@NonNull Context context) {
        List<GlassQuickShortcut> list = new ArrayList<>();

        // Addons (from declared addon registry infos)
        try {
            AddonManager mgr = AddonManager.get();
            if (mgr != null) {
                List<AddonInfo> addons = mgr.getAddonInfos();
                if (addons != null) {
                    for (AddonInfo info : addons) {
                        if (info != null && info.addonName != 0) {
                            String title = context.getString(info.addonName);
                            list.add(new GlassQuickShortcut("addon:" + info.addonId, title, info.icon, "addon"));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Library destinations
        list.add(new GlassQuickShortcut("lib:playlists", safeGetString(context, R.string.playlists, "Playlists"), R.drawable.playlist, "library"));
        list.add(new GlassQuickShortcut("lib:folders", safeGetString(context, R.string.folders, "Folders"), R.drawable.add_folder, "library"));
        list.add(new GlassQuickShortcut("lib:favorites", safeGetString(context, R.string.favorites, "Favorites"), R.drawable.favorite, "library"));
        list.add(new GlassQuickShortcut("lib:recent", safeGetString(context, R.string.recent, "Recent"), R.drawable.timer, "library"));
        list.add(new GlassQuickShortcut("car_finder", "Car Finder", R.drawable.ic_car_find, "addon"));
        list.add(new GlassQuickShortcut("settings", safeGetString(context, R.string.settings, "Settings"), R.drawable.settings, "system"));

        return list;
    }

    private static String safeGetString(@NonNull Context context, int resId, String fallback) {
        try {
            if (resId != 0) {
                return context.getString(resId);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static void show(@NonNull Context context, @NonNull OnShortcutSelectedListener listener) {
        show(context, (me.aap.fermata.ui.activity.MainActivityDelegate) null, listener);
    }

    public static void show(@NonNull Context context, @Nullable me.aap.fermata.ui.activity.MainActivityDelegate activity, @NonNull OnShortcutSelectedListener listener) {
        List<GlassQuickShortcut> shortcuts = getAvailableShortcuts(context);

        CharSequence[] titles = new CharSequence[shortcuts.size()];
        for (int i = 0; i < shortcuts.size(); i++) {
            titles[i] = shortcuts.get(i).getTitle();
        }

        me.aap.fermata.ui.activity.MainActivityDelegate a = (activity != null) ? activity : me.aap.fermata.ui.activity.MainActivityDelegate.get(context);
        me.aap.utils.ui.view.DialogBuilder builder = (a != null) ? a.createDialogBuilder(context) : me.aap.utils.ui.activity.ActivityDelegate.get(context).createDialogBuilder(context);
        builder.setTitle(null, safeGetString(context, R.string.theme_glass, "Glass") + " Shortcuts");
        builder.setSingleChoiceItems(titles, -1, (dialog, which) -> {
            if (which >= 0 && which < shortcuts.size()) {
                listener.onShortcutSelected(shortcuts.get(which));
            }
            if (dialog != null) dialog.dismiss();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}
