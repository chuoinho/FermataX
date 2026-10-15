package me.aap.fermata.ui.glass.quickbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.PreferenceStore;

import android.app.AlertDialog;
import android.content.Context;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.glass.dialog.GlassDialogBuilder;
import me.aap.utils.ui.view.DialogBuilder;

/**
 * Manager handling the 6 customizable shortcut slots in the automotive Glass Quick Bar.
 * All slots (0-5) are user-customizable. Addons and Exit are moved to the Glass Navigation Rail.
 */
public class GlassQuickBarManager {

    public static final int TOTAL_SLOTS = 6;
    public static final int CUSTOM_SLOTS_COUNT = TOTAL_SLOTS;

    private final PreferenceStore store;
    private final GlassQuickShortcut[] customSlots = new GlassQuickShortcut[TOTAL_SLOTS];

    public GlassQuickBarManager(@NonNull PreferenceStore store) {
        this.store = store;
        loadFromPreferences();
    }

    @Nullable
    public synchronized GlassQuickShortcut getCustomShortcut(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return customSlots[index];
    }

    @Nullable
    public synchronized GlassQuickShortcut getSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= TOTAL_SLOTS) return null;
        return customSlots[slotIndex];
    }

    public synchronized void setCustomShortcut(int index, @Nullable GlassQuickShortcut shortcut) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot index out of range: " + index + " (must be 0-5)");
        }
        customSlots[index] = shortcut;
        saveToPreferences();
    }

    public synchronized void removeCustomShortcut(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            throw new IllegalArgumentException("Slot index out of range: " + index + " (must be 0-5)");
        }
        customSlots[index] = null;
        saveToPreferences();
    }

    public static void showManageDialog(@NonNull Context ctx, @Nullable MainActivityDelegate activity, @Nullable Runnable onUpdated) {
        MainActivityDelegate a = (activity != null) ? activity : MainActivityDelegate.get(ctx);
        PreferenceStore store = (a != null) ? a.getPrefs() : MainActivityPrefs.get();
        GlassQuickBarManager mgr = new GlassQuickBarManager(store);
        CharSequence[] slotTitles = new CharSequence[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            GlassQuickShortcut s = mgr.getSlot(i);
            slotTitles[i] = "Slot " + (i + 1) + ": " + ((s != null) ? s.getTitle() : "[Empty]");
        }
        DialogBuilder builder = (a != null) ? a.createDialogBuilder(ctx) : me.aap.utils.ui.activity.ActivityDelegate.get(ctx).createDialogBuilder(ctx);
        builder.setTitle(null, "Quick Dock Shortcuts (6 Slots)");
        builder.setSingleChoiceItems(slotTitles, -1, (d, slotIdx) -> {
            if (d != null) d.dismiss();
            GlassQuickShortcut cur = mgr.getSlot(slotIdx);
            if (cur == null) {
                GlassShortcutPickerDialog.show(ctx, a, selected -> {
                    mgr.setCustomShortcut(slotIdx, selected);
                    if (onUpdated != null) onUpdated.run();
                });
            } else {
                DialogBuilder subBuilder = (a != null) ? a.createDialogBuilder(ctx) : me.aap.utils.ui.activity.ActivityDelegate.get(ctx).createDialogBuilder(ctx);
                subBuilder.setTitle(null, "Slot " + (slotIdx + 1) + ": " + cur.getTitle());
                subBuilder.setSingleChoiceItems(new CharSequence[]{"Change", "Clear"}, -1, (subD, choice) -> {
                    if (choice == 0) {
                        GlassShortcutPickerDialog.show(ctx, a, selected -> {
                            mgr.setCustomShortcut(slotIdx, selected);
                            if (onUpdated != null) onUpdated.run();
                        });
                    } else {
                        mgr.removeCustomShortcut(slotIdx);
                        if (onUpdated != null) onUpdated.run();
                    }
                    if (subD != null) subD.dismiss();
                });
                subBuilder.setNegativeButton(android.R.string.cancel, null);
                subBuilder.show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void loadFromPreferences() {
        String jsonStr = store.getStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS);
        if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                customSlots[i] = null;
            }
            return;
        }

        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (i < array.length() && !array.isNull(i)) {
                    JSONObject obj = array.optJSONObject(i);
                    GlassQuickShortcut shortcut = (obj != null) ? GlassQuickShortcut.fromJson(obj) : null;
                    // Migrate away from old hardcoded fixed slots "ADDON_CATALOG" and "EXIT"
                    if (shortcut != null && ("ADDON_CATALOG".equals(shortcut.getId()) || "EXIT".equals(shortcut.getId()))) {
                        customSlots[i] = null;
                    } else {
                        customSlots[i] = shortcut;
                    }
                } else {
                    customSlots[i] = null;
                }
            }
        } catch (JSONException e) {
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                customSlots[i] = null;
            }
        }
    }

    private void saveToPreferences() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (customSlots[i] != null) {
                array.put(customSlots[i].toJson());
            } else {
                array.put(JSONObject.NULL);
            }
        }
        store.applyStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS, array.toString());
    }
}
