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

import me.aap.fermata.R;
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
    public void testCleanInstallSlotsAreEmptyAndFixedSlotsExist() {
        // Customizable slots 0-3 must be empty on clean install
        for (int i = 0; i < 4; i++) {
            assertNull(manager.getCustomShortcut(i));
            assertNull(manager.getSlot(i));
        }

        // Fixed slots 4 (Addon Catalog) and 5 (Exit) must always exist
        assertNotNull(manager.getFixedSlot(4));
        assertEquals("ADDON_CATALOG", manager.getFixedSlot(4).getId());
        assertEquals("Addons", manager.getFixedSlot(4).getTitle());

        assertNotNull(manager.getFixedSlot(5));
        assertEquals("EXIT", manager.getFixedSlot(5).getId());
        assertEquals("Exit", manager.getFixedSlot(5).getTitle());

        assertEquals(manager.getFixedSlot(4), manager.getSlot(4));
        assertEquals(manager.getFixedSlot(5), manager.getSlot(5));
    }

    @Test
    public void testAddUpdateAndRemoveCustomShortcut() {
        GlassQuickShortcut yt = new GlassQuickShortcut("addon:youtube", "YouTube", R.drawable.youtube, "addon");
        manager.setCustomShortcut(0, yt);

        assertEquals(yt, manager.getCustomShortcut(0));
        assertEquals(yt, manager.getSlot(0));

        // Recreate manager with same preference store to test persistence
        GlassQuickBarManager reloaded = new GlassQuickBarManager(store);
        GlassQuickShortcut loadedYt = reloaded.getCustomShortcut(0);
        assertNotNull(loadedYt);
        assertEquals("addon:youtube", loadedYt.getId());
        assertEquals("YouTube", loadedYt.getTitle());

        // Remove shortcut
        reloaded.removeCustomShortcut(0);
        assertNull(reloaded.getCustomShortcut(0));

        GlassQuickBarManager reloadedAfterRemoval = new GlassQuickBarManager(store);
        assertNull(reloadedAfterRemoval.getCustomShortcut(0));
    }

    @Test
    public void testFixedSlotsCannotBeOverwritten() {
        GlassQuickShortcut custom = new GlassQuickShortcut("custom:test", "Test", 0, "custom");
        assertThrows(IllegalArgumentException.class, () -> manager.setCustomShortcut(4, custom));
        assertThrows(IllegalArgumentException.class, () -> manager.setCustomShortcut(5, custom));
    }

    @Test
    public void testGlassQuickBarViewRendering() {
        Context context = RuntimeEnvironment.getApplication();
        GlassQuickBarView view = new GlassQuickBarView(context);
        assertNotNull(view);

        AtomicBoolean catalogClicked = new AtomicBoolean(false);
        AtomicBoolean exitClicked = new AtomicBoolean(false);

        view.init(manager, new GlassQuickBarView.Listener() {
            @Override
            public void onShortcutClicked(int slotIndex, GlassQuickShortcut shortcut) {}

            @Override
            public void onEmptySlotClicked(int slotIndex) {}

            @Override
            public void onShortcutLongClicked(int slotIndex, GlassQuickShortcut shortcut) {}

            @Override
            public void onAddonsCatalogClicked() {
                catalogClicked.set(true);
            }

            @Override
            public void onExitClicked() {
                exitClicked.set(true);
            }
        });

        assertEquals(GlassQuickBarManager.TOTAL_SLOTS, view.getChildCount());

        // Simulate click on Addons (slot 4)
        view.getChildAt(4).performClick();
        assertTrue(catalogClicked.get());

        // Simulate click on Exit (slot 5)
        view.getChildAt(5).performClick();
        assertTrue(exitClicked.get());
    }

    @Test
    public void testAvailableShortcutsList() {
        Context context = RuntimeEnvironment.getApplication();
        List<GlassQuickShortcut> shortcuts = GlassShortcutPickerDialog.getAvailableShortcuts(context);
        assertNotNull(shortcuts);
        assertFalse(shortcuts.isEmpty());
    }
}
