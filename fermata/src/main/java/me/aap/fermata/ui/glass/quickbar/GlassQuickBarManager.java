package me.aap.fermata.ui.glass.quickbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.PreferenceStore;

/**
 * Manager handling the 6 slots in the automotive Glass Quick Bar.
 * Slots 0-3 are user-customizable (default: empty).
 * Slot 4 is permanently bound to Addon Catalog.
 * Slot 5 is permanently bound to Exit.
 */
public class GlassQuickBarManager {

    public static final int TOTAL_SLOTS = 6;
    public static final int CUSTOM_SLOTS_COUNT = 4;

    public static final GlassQuickShortcut SLOT_ADDON_CATALOG =
            new GlassQuickShortcut("ADDON_CATALOG", "Addons", R.drawable.view_grid, "fixed");

    public static final GlassQuickShortcut SLOT_EXIT =
            new GlassQuickShortcut("EXIT", "Exit", R.drawable.exit, "fixed");

    private final PreferenceStore store;
    private final GlassQuickShortcut[] customSlots = new GlassQuickShortcut[CUSTOM_SLOTS_COUNT];

    public GlassQuickBarManager(@NonNull PreferenceStore store) {
        this.store = store;
        loadFromPreferences();
    }

    @Nullable
    public synchronized GlassQuickShortcut getCustomShortcut(int index) {
        if (index < 0 || index >= CUSTOM_SLOTS_COUNT) return null;
        return customSlots[index];
    }

    @Nullable
    public GlassQuickShortcut getFixedSlot(int index) {
        if (index == 4) return SLOT_ADDON_CATALOG;
        if (index == 5) return SLOT_EXIT;
        return null;
    }

    @Nullable
    public synchronized GlassQuickShortcut getSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < CUSTOM_SLOTS_COUNT) {
            return customSlots[slotIndex];
        }
        return getFixedSlot(slotIndex);
    }

    public synchronized void setCustomShortcut(int index, @Nullable GlassQuickShortcut shortcut) {
        if (index < 0 || index >= CUSTOM_SLOTS_COUNT) {
            throw new IllegalArgumentException("Cannot customize slot " + index + "; must be 0-3.");
        }
        customSlots[index] = shortcut;
        saveToPreferences();
    }

    public synchronized void removeCustomShortcut(int index) {
        if (index < 0 || index >= CUSTOM_SLOTS_COUNT) {
            throw new IllegalArgumentException("Cannot customize slot " + index + "; must be 0-3.");
        }
        customSlots[index] = null;
        saveToPreferences();
    }

    private void loadFromPreferences() {
        String jsonStr = store.getStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS);
        if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
            for (int i = 0; i < CUSTOM_SLOTS_COUNT; i++) {
                customSlots[i] = null;
            }
            return;
        }

        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < CUSTOM_SLOTS_COUNT; i++) {
                if (i < array.length() && !array.isNull(i)) {
                    JSONObject obj = array.optJSONObject(i);
                    customSlots[i] = (obj != null) ? GlassQuickShortcut.fromJson(obj) : null;
                } else {
                    customSlots[i] = null;
                }
            }
        } catch (JSONException e) {
            for (int i = 0; i < CUSTOM_SLOTS_COUNT; i++) {
                customSlots[i] = null;
            }
        }
    }

    private void saveToPreferences() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < CUSTOM_SLOTS_COUNT; i++) {
            if (customSlots[i] != null) {
                array.put(customSlots[i].toJson());
            } else {
                array.put(JSONObject.NULL);
            }
        }
        store.applyStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS, array.toString());
    }
}
