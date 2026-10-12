package me.aap.fermata.ui.glass.quickbar;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;

/**
 * Picker dialog allowing users to choose an addon or library destination for a quick bar slot.
 */
public class GlassShortcutPickerDialog {

    public interface OnShortcutSelectedListener {
        void onShortcutSelected(@NonNull GlassQuickShortcut shortcut);
    }

    public static List<GlassQuickShortcut> getAvailableShortcuts(@NonNull Context context) {
        List<GlassQuickShortcut> list = new ArrayList<>();

        // Addons
        try {
            AddonManager mgr = AddonManager.get();
            if (mgr != null) {
                Collection<FermataAddon> addons = mgr.getAddons();
                if (addons != null) {
                    for (FermataAddon addon : addons) {
                        if (addon != null) {
                            AddonInfo info = addon.getInfo();
                            if (info != null && info.addonName != 0) {
                                String title = context.getString(info.addonName);
                                list.add(new GlassQuickShortcut("addon:" + info.addonId, title, info.icon, "addon"));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Library destinations
        list.add(new GlassQuickShortcut("lib:playlists", "Playlists", R.drawable.playlist, "library"));
        list.add(new GlassQuickShortcut("lib:folders", "Folders", R.drawable.add_folder, "library"));
        list.add(new GlassQuickShortcut("lib:favorites", "Favorites", R.drawable.favorite, "library"));
        list.add(new GlassQuickShortcut("lib:recent", "Recent", R.drawable.timer, "library"));

        return list;
    }

    public static void show(@NonNull Context context, @NonNull OnShortcutSelectedListener listener) {
        List<GlassQuickShortcut> shortcuts = getAvailableShortcuts(context);

        String[] titles = new String[shortcuts.size()];
        for (int i = 0; i < shortcuts.size(); i++) {
            titles[i] = shortcuts.get(i).getTitle();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Shortcut");
        builder.setItems(titles, (dialog, which) -> {
            if (which >= 0 && which < shortcuts.size()) {
                listener.onShortcutSelected(shortcuts.get(which));
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}
