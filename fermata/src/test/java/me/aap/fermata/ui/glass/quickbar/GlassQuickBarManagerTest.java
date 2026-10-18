package me.aap.fermata.ui.glass.quickbar;

import static org.junit.Assert.*;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;

@RunWith(RobolectricTestRunner.class)
public class GlassQuickBarManagerTest {

    private PreferenceStore store;
    private GlassQuickBarManager manager;

    @Before
    public void setUp() {
        store = new BasicPreferenceStore();
        manager = new GlassQuickBarManager(store);
    }

    @Test
    public void testCleanInstallAll6SlotsAreEmpty() {
        // All 6 customizable slots must be empty on clean install
        for (int i = 0; i < GlassQuickBarManager.TOTAL_SLOTS; i++) {
            assertNull(manager.getCustomShortcut(i));
            assertNull(manager.getSlot(i));
        }
    }

    @Test
    public void testAddUpdateAndRemoveCustomShortcutAcrossAllSlots() {
        GlassQuickShortcut yt = new GlassQuickShortcut("addon:youtube", "YouTube", R.drawable.youtube, "addon");
        manager.setCustomShortcut(0, yt);

        GlassQuickShortcut tv = new GlassQuickShortcut("addon:tv", "TV", R.drawable.tv, "addon");
        manager.setCustomShortcut(5, tv); // Slot 5 is now fully customizable!

        assertEquals(yt, manager.getCustomShortcut(0));
        assertEquals(yt, manager.getSlot(0));
        assertEquals(tv, manager.getCustomShortcut(5));
        assertEquals(tv, manager.getSlot(5));

        // Recreate manager with same preference store to test persistence
        GlassQuickBarManager reloaded = new GlassQuickBarManager(store);
        GlassQuickShortcut loadedYt = reloaded.getCustomShortcut(0);
        assertNotNull(loadedYt);
        assertEquals("addon:me.aap.fermata.addon.web.yt.YoutubeAddon", loadedYt.getId());

        GlassQuickShortcut loadedTv = reloaded.getCustomShortcut(5);
        assertNotNull(loadedTv);
        assertEquals("addon:me.aap.fermata.addon.tv.TvAddon", loadedTv.getId());

        // Remove shortcut
        reloaded.removeCustomShortcut(0);
        assertNull(reloaded.getCustomShortcut(0));

        GlassQuickBarManager reloadedAfterRemoval = new GlassQuickBarManager(store);
        assertNull(reloadedAfterRemoval.getCustomShortcut(0));
        assertNotNull(reloadedAfterRemoval.getCustomShortcut(5));
    }

    @Test
    public void testOutOfBoundsSlotIndexThrows() {
        GlassQuickShortcut custom = new GlassQuickShortcut("custom:test", "Test", 0, "custom");
        assertThrows(IllegalArgumentException.class, () -> manager.setCustomShortcut(-1, custom));
        assertThrows(IllegalArgumentException.class, () -> manager.setCustomShortcut(6, custom));
    }

    @Test
    public void testMigrationCleansLegacyFixedSlots() {
        // Simulate an older preference JSON with ADDON_CATALOG and EXIT in slots 4 and 5
        String legacyJson = "[{\"id\":\"addon:1\",\"title\":\"App1\",\"icon\":0,\"type\":\"addon\"},null,null,null,{\"id\":\"ADDON_CATALOG\",\"title\":\"Addons\",\"icon\":0,\"type\":\"fixed\"},{\"id\":\"EXIT\",\"title\":\"Exit\",\"icon\":0,\"type\":\"fixed\"}]";
        PreferenceStore legacyStore = new BasicPreferenceStore();
        legacyStore.applyStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS, legacyJson);

        GlassQuickBarManager migrated = new GlassQuickBarManager(legacyStore);
        // Slot 0 is preserved
        assertNotNull(migrated.getSlot(0));
        assertEquals("addon:1", migrated.getSlot(0).getId());

        // Slots 4 and 5 must be migrated to clean null customizable slots
        assertNull(migrated.getSlot(4));
        assertNull(migrated.getSlot(5));
    }

    @Test
    public void testGlassQuickBarViewRenderingAndInteractions() {
        Context context = RuntimeEnvironment.getApplication();
        GlassQuickBarView view = new GlassQuickBarView(context);
        assertNotNull(view);

        GlassQuickShortcut yt = new GlassQuickShortcut("addon:youtube", "YouTube", R.drawable.youtube, "addon");
        manager.setCustomShortcut(0, yt);

        AtomicInteger clickedSlot = new AtomicInteger(-1);
        AtomicInteger emptyClickedSlot = new AtomicInteger(-1);

        view.init(manager, new GlassQuickBarView.Listener() {
            @Override
            public void onShortcutClicked(int slotIndex, GlassQuickShortcut shortcut) {
                clickedSlot.set(slotIndex);
            }

            @Override
            public void onEmptySlotClicked(int slotIndex) {
                emptyClickedSlot.set(slotIndex);
            }

            @Override
            public void onShortcutLongClicked(int slotIndex, GlassQuickShortcut shortcut) {}
        });

        assertEquals(view.getActiveSlotCount(), view.getChildCount());

        // Click assigned slot 0
        view.getChildAt(0).performClick();
        assertEquals(0, clickedSlot.get());

        // Click empty slot 1
        view.getChildAt(1).performClick();
        assertEquals(1, emptyClickedSlot.get());
    }

    @Test
    public void testAvailableShortcutsList() {
        Context context = RuntimeEnvironment.getApplication();
        List<GlassQuickShortcut> shortcuts = GlassShortcutPickerDialog.getAvailableShortcuts(context);
        assertNotNull(shortcuts);
        assertFalse(shortcuts.isEmpty());
        // Verify Google Cast is excluded
        for (GlassQuickShortcut s : shortcuts) {
            assertFalse(s.getId().toLowerCase().contains("cast"));
            assertFalse(s.getTitle().toLowerCase().contains("cast"));
        }
    }

    @Test
    public void testResolveAddonInfoByLegacyIdAndTitle() {
        Context context = RuntimeEnvironment.getApplication();

        // 1. Stale numeric ID with title "Youtube"
        GlassQuickShortcut staleYt = new GlassQuickShortcut("addon:2131297099", "Youtube", 0, "addon");
        me.aap.fermata.addon.AddonInfo ytInfo = GlassQuickBarManager.resolveAddonInfo(staleYt, context);
        assertNotNull(ytInfo);
        assertEquals("me.aap.fermata.addon.web.yt.YoutubeAddon", ytInfo.className);

        // 2. Stale numeric ID with title "TV"
        GlassQuickShortcut staleTv = new GlassQuickShortcut("addon:2131297054", "TV", 0, "addon");
        me.aap.fermata.addon.AddonInfo tvInfo = GlassQuickBarManager.resolveAddonInfo(staleTv, context);
        assertNotNull(tvInfo);
        assertEquals("me.aap.fermata.addon.tv.TvAddon", tvInfo.className);

        // 3. Stale numeric ID with title "Web browser"
        GlassQuickShortcut staleWeb = new GlassQuickShortcut("addon:2131297086", "Web browser", 0, "addon");
        me.aap.fermata.addon.AddonInfo webInfo = GlassQuickBarManager.resolveAddonInfo(staleWeb, context);
        assertNotNull(webInfo);
        assertEquals("me.aap.fermata.addon.web.WebBrowserAddon", webInfo.className);

        // 4. Short alias "addon:youtube"
        GlassQuickShortcut aliasYt = new GlassQuickShortcut("addon:youtube", "YouTube", 0, "addon");
        me.aap.fermata.addon.AddonInfo aliasYtInfo = GlassQuickBarManager.resolveAddonInfo(aliasYt, context);
        assertNotNull(aliasYtInfo);
        assertEquals("me.aap.fermata.addon.web.yt.YoutubeAddon", aliasYtInfo.className);

        // 5. Canonical class name "addon:me.aap.fermata.addon.web.yt.YoutubeAddon"
        GlassQuickShortcut canonicalYt = new GlassQuickShortcut("addon:me.aap.fermata.addon.web.yt.YoutubeAddon", "YouTube", 0, "addon");
        me.aap.fermata.addon.AddonInfo canonicalYtInfo = GlassQuickBarManager.resolveAddonInfo(canonicalYt, context);
        assertNotNull(canonicalYtInfo);
        assertEquals("me.aap.fermata.addon.web.yt.YoutubeAddon", canonicalYtInfo.className);
    }

    @Test
    public void testMigrationAutoCorrectsStalePreferences() {
        // Simulating the exact JSON found on the device
        String deviceJson = "[{\"id\":\"addon:2131297054\",\"title\":\"TV\",\"iconResId\":2131231211,\"type\":\"addon\"}," +
                "{\"id\":\"addon:2131297099\",\"title\":\"Youtube\",\"iconResId\":2131231219,\"type\":\"addon\"}," +
                "{\"id\":\"addon:2131297086\",\"title\":\"Web browser\",\"iconResId\":2131231218,\"type\":\"addon\"}," +
                "null,null,null]";

        PreferenceStore devStore = new BasicPreferenceStore();
        devStore.applyStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS, deviceJson);

        GlassQuickBarManager migratedMgr = new GlassQuickBarManager(devStore);

        // Verify slot 0 (TV) migrated to canonical class name and fresh icon
        GlassQuickShortcut tvSlot = migratedMgr.getSlot(0);
        assertNotNull(tvSlot);
        assertEquals("addon:me.aap.fermata.addon.tv.TvAddon", tvSlot.getId());
        assertEquals(R.drawable.tv, tvSlot.getIconResId());

        // Verify slot 1 (Youtube) migrated to canonical class name and fresh icon
        GlassQuickShortcut ytSlot = migratedMgr.getSlot(1);
        assertNotNull(ytSlot);
        assertEquals("addon:me.aap.fermata.addon.web.yt.YoutubeAddon", ytSlot.getId());
        assertEquals(R.drawable.youtube, ytSlot.getIconResId());

        // Verify slot 2 (Web browser) migrated to canonical class name and fresh icon
        GlassQuickShortcut webSlot = migratedMgr.getSlot(2);
        assertNotNull(webSlot);
        assertEquals("addon:me.aap.fermata.addon.web.WebBrowserAddon", webSlot.getId());
        assertEquals(R.drawable.web, webSlot.getIconResId());

        // Verify the preference store was updated with canonical IDs
        String updatedJson = devStore.getStringPref(MainActivityPrefs.PREF_GLASS_QUICK_SHORTCUTS);
        assertTrue(updatedJson.contains("me.aap.fermata.addon.web.yt.YoutubeAddon"));
        assertTrue(updatedJson.contains("me.aap.fermata.addon.tv.TvAddon"));
        assertTrue(updatedJson.contains("me.aap.fermata.addon.web.WebBrowserAddon"));
    }
}
