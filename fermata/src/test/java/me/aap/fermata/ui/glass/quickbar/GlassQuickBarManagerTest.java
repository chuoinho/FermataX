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
        assertEquals("addon:youtube", loadedYt.getId());

        GlassQuickShortcut loadedTv = reloaded.getCustomShortcut(5);
        assertNotNull(loadedTv);
        assertEquals("addon:tv", loadedTv.getId());

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
    }
}
