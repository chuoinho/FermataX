package me.aap.fermata.ui.glass.quickbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.PreferenceStore;

import android.app.AlertDialog;
import android.content.Context;
import java.util.List;
import java.util.Locale;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonRegistry;
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
        String emptyStr = safeGetString(ctx, R.string.glass_quickbar_empty, "[Empty]");
        String slotFmt = safeGetString(ctx, R.string.glass_quickbar_slot_format, "Slot %1$d: %2$s");
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            GlassQuickShortcut s = mgr.getSlot(i);
            slotTitles[i] = String.format(Locale.getDefault(), slotFmt, (i + 1), ((s != null) ? s.getTitle() : emptyStr));
        }
        DialogBuilder builder = (a != null) ? a.createDialogBuilder(ctx) : me.aap.utils.ui.activity.ActivityDelegate.get(ctx).createDialogBuilder(ctx);
        builder.setTitle(null, safeGetString(ctx, R.string.glass_quickbar_title, "Quick Dock Shortcuts (6 Slots)"));
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
                subBuilder.setTitle(null, String.format(Locale.getDefault(), slotFmt, (slotIdx + 1), cur.getTitle()));
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

    public void reload() {
        loadFromPreferences();
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
            boolean needsMigration = false;
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (i < array.length() && !array.isNull(i)) {
                    JSONObject obj = array.optJSONObject(i);
                    GlassQuickShortcut shortcut = (obj != null) ? GlassQuickShortcut.fromJson(obj) : null;
                    // Migrate away from old hardcoded fixed slots "ADDON_CATALOG" and "EXIT"
                    if (shortcut != null && ("ADDON_CATALOG".equals(shortcut.getId()) || "EXIT".equals(shortcut.getId()))) {
                        customSlots[i] = null;
                        needsMigration = true;
                    } else if (shortcut != null && ("addon".equals(shortcut.getType()) || shortcut.getId().startsWith("addon:"))) {
                        // Resolve addon info to sanitize stale numeric IDs and stale icon IDs
                        AddonInfo resolved = resolveAddonInfo(shortcut, null);
                        if (resolved != null) {
                            String canonicalId = "addon:" + resolved.className;
                            int canonicalIcon = (resolved.icon != 0) ? resolved.icon : shortcut.getIconResId();
                            if (!canonicalId.equals(shortcut.getId()) || canonicalIcon != shortcut.getIconResId()) {
                                shortcut = new GlassQuickShortcut(canonicalId, shortcut.getTitle(), canonicalIcon, "addon");
                                needsMigration = true;
                            }
                        }
                        customSlots[i] = shortcut;
                    } else {
                        customSlots[i] = shortcut;
                    }
                } else {
                    customSlots[i] = null;
                }
            }
            if (needsMigration) {
                saveToPreferences();
            }
        } catch (JSONException e) {
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                customSlots[i] = null;
            }
        }
    }

    /**
     * Resolves the canonical AddonInfo corresponding to a shortcut across compile-time AAPT ID changes,
     * class names, module aliases, voice targets, or localized titles.
     */
    @Nullable
    public static AddonInfo resolveAddonInfo(@NonNull GlassQuickShortcut shortcut, @Nullable Context context) {
        String rawId = shortcut.getId();
        String key = rawId.startsWith("addon:") ? rawId.substring(6) : rawId;

        AddonManager mgr = null;
        try {
            mgr = AddonManager.get();
        } catch (Throwable ignored) {}

        AddonRegistry registry = AddonRegistry.get();
        if (mgr == null && registry == null) return null;

        // 1. Direct class name or module lookup in AddonManager registry
        AddonInfo info = (mgr != null) ? mgr.getAddonInfo(key) : registry.getAvailable(key);
        if (info != null) return info;

        // 2. Numeric ID lookup (if key is a valid integer R.id from the current build)
        try {
            int numId = Integer.parseInt(key);
            if (numId != 0) {
                info = (mgr != null) ? mgr.getAddonInfo(numId) : registry.getAvailable(numId);
                if (info != null) return info;
            }
        } catch (NumberFormatException ignored) {}

        List<AddonInfo> available = (mgr != null) ? mgr.getAddonInfos() : registry.getAvailable();
        if (available == null || available.isEmpty()) return null;

        // 3. Match by className (exact, endsWith, or case-insensitive contains)
        for (AddonInfo candidate : available) {
            if (candidate.className.equalsIgnoreCase(key) ||
                    candidate.className.endsWith("." + key) ||
                    candidate.className.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        // 4. Match by moduleName, voiceTarget, or resolverSchemes
        for (AddonInfo candidate : available) {
            if (candidate.moduleName.equalsIgnoreCase(key) ||
                    candidate.voiceTarget.equalsIgnoreCase(key) ||
                    candidate.hasResolverScheme(key)) {
                return candidate;
            }
        }

        // 5. Match by localized title or brand title
        String title = shortcut.getTitle();
        if (title != null && !title.isEmpty()) {
            // Pass A: Exact localized title match
            for (AddonInfo candidate : available) {
                if (candidate.addonName != 0 && context != null) {
                    try {
                        String candTitle = context.getString(candidate.addonName);
                        if (title.equalsIgnoreCase(candTitle)) return candidate;
                    } catch (Throwable ignored) {}
                }
            }

            // Pass B: Domain-specific match (isAddonMatch)
            for (AddonInfo candidate : available) {
                if (isAddonMatch(candidate, title)) {
                    return candidate;
                }
            }

            // Pass C: voiceTarget exact or substring
            for (AddonInfo candidate : available) {
                if (!candidate.voiceTarget.isEmpty() &&
                        (title.equalsIgnoreCase(candidate.voiceTarget) ||
                                title.toLowerCase(Locale.ROOT).contains(candidate.voiceTarget.toLowerCase(Locale.ROOT)))) {
                    return candidate;
                }
            }

            // Pass D: Unique module name only
            for (AddonInfo candidate : available) {
                if (registry != null && registry.isUniqueModule(candidate) &&
                        title.equalsIgnoreCase(candidate.moduleName)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static boolean isAddonMatch(AddonInfo candidate, String title) {
        String t = title.toLowerCase(Locale.ROOT).trim();
        String c = candidate.className.toLowerCase(Locale.ROOT);
        if (t.contains("youtube") && c.contains("youtube")) return true;
        if ((t.contains("web") || t.contains("browser") || t.contains("duyệt")) && c.contains("webbrowser")) return true;
        if (t.contains("tv") && c.contains("tv")) return true;
        if (t.contains("radio") && c.contains("radio")) return true;
        if (t.contains("podcast") && c.contains("podcast")) return true;
        if ((t.contains("audiobook") || t.contains("sách nói") || t.contains("audio")) && c.contains("audiobook")) return true;
        if (t.contains("stremio") && c.contains("stremio")) return true;
        if (t.contains("tiktok") && c.contains("tiktok")) return true;
        return false;
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

    private static String safeGetString(@Nullable Context context, int resId, String fallback) {
        if (context == null) return fallback;
        try {
            return context.getString(resId);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
