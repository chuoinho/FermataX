package me.aap.fermata.ui.glass.catalog;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.R;

@RunWith(RobolectricTestRunner.class)
public class GlassAddonsCatalogFragmentTest {

    @Test
    public void testColumnCountComputation() {
        assertEquals(4, GlassAddonsCatalogFragment.computeColumnCount(1280, 2.0f));
        assertEquals(6, GlassAddonsCatalogFragment.computeColumnCount(1920, 2.0f));
    }

    @Test
    public void testFragmentId() {
        GlassAddonsCatalogFragment fragment = new GlassAddonsCatalogFragment();
        assertEquals(R.id.glass_addons_catalog_fragment, fragment.getFragmentId());
    }

    @Test
    public void testHolderRebindPaletteDayNightDay() {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        GlassAddonCardHolder holder = GlassAddonCardHolder.create(ctx, false); // created in Day mode

        GlassAddonsCatalogFragment.CatalogItem item1 = new GlassAddonsCatalogFragment.CatalogItem(
                1, "YouTube", "Watch videos", R.drawable.youtube, 0xFFFF0000, true);

        // 1. Bind in Day mode
        holder.bind(item1, false);
        me.aap.fermata.ui.glass.theme.GlassThemeColors dayColors = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, false);
        assertEquals("YouTube", holder.titleView.getText().toString());
        assertEquals(dayColors.getTextPrimary(), holder.titleView.getCurrentTextColor());
        assertEquals(dayColors.getTextSecondary(), holder.descView.getCurrentTextColor());

        // 2. Re-bind SAME holder in Night mode (e.g. appearance toggled without recreating view)
        holder.bind(item1, true);
        me.aap.fermata.ui.glass.theme.GlassThemeColors nightColors = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, true);
        assertEquals(nightColors.getTextPrimary(), holder.titleView.getCurrentTextColor());
        assertEquals(nightColors.getTextSecondary(), holder.descView.getCurrentTextColor());
        assertNotEquals(dayColors.getTextPrimary(), nightColors.getTextPrimary());

        // 3. Re-bind SAME holder back to Day mode
        holder.bind(item1, false);
        assertEquals(dayColors.getTextPrimary(), holder.titleView.getCurrentTextColor());
        assertEquals(dayColors.getTextSecondary(), holder.descView.getCurrentTextColor());
    }

    @Test
    public void testHolderRecycleDifferentItemsWithPalette() {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        GlassAddonCardHolder holder = GlassAddonCardHolder.create(ctx, true);

        GlassAddonsCatalogFragment.CatalogItem itemA = new GlassAddonsCatalogFragment.CatalogItem(
                1, "Item A", "Desc A", R.drawable.settings, 0, false);
        GlassAddonsCatalogFragment.CatalogItem itemB = new GlassAddonsCatalogFragment.CatalogItem(
                2, "Item B", "Desc B", R.drawable.favorite, 0xFFE91E63, false);

        // Bind item A in Night
        holder.bind(itemA, true);
        assertEquals("Item A", holder.titleView.getText().toString());

        // Recycle for item B in Day
        holder.bind(itemB, false);
        assertEquals("Item B", holder.titleView.getText().toString());
        me.aap.fermata.ui.glass.theme.GlassThemeColors dayColors = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, false);
        assertEquals(dayColors.getTextPrimary(), holder.titleView.getCurrentTextColor());
        assertEquals(dayColors.getTextSecondary(), holder.descView.getCurrentTextColor());
        assertNotNull(holder.iconView.getImageTintList());
        assertEquals(0xFFE91E63, holder.iconView.getImageTintList().getDefaultColor());
    }

    @Test
    public void testEditModeHolderBind() {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        GlassAddonCardHolder holder = GlassAddonCardHolder.create(ctx, false);

        GlassAddonsCatalogFragment.CatalogItem enabledItem = new GlassAddonsCatalogFragment.CatalogItem(
                "youtube", 1, "YouTube", "Videos", R.drawable.youtube, 0xFFFF0000, true, null, true);
        GlassAddonsCatalogFragment.CatalogItem disabledItem = new GlassAddonsCatalogFragment.CatalogItem(
                "tv", 2, "TV", "Live TV", R.drawable.tv, 0xFF2196F3, true, null, false);

        // In edit mode: enabled item
        holder.bind(enabledItem, false, true);
        me.aap.fermata.ui.glass.theme.GlassThemeColors dayColors = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, false);
        assertEquals(1.0f, holder.itemView.getAlpha(), 0.01f);
        assertTrue(holder.descView.getText().toString().contains("Enabled"));
        assertEquals(dayColors.getTextSecondary(), holder.descView.getCurrentTextColor());

        // In edit mode: disabled item
        holder.bind(disabledItem, false, true);
        assertTrue(holder.itemView.getAlpha() < 0.5f);
        assertTrue(holder.descView.getText().toString().contains("Disabled"));
        assertEquals(dayColors.getTextSecondary(), holder.descView.getCurrentTextColor());
    }

    @Test
    public void testToolBarMediatorExists() {
        GlassAddonsCatalogFragment fragment = new GlassAddonsCatalogFragment();
        assertNotNull(fragment.getToolBarMediator());
        assertTrue(fragment.getToolBarMediator() instanceof GlassAddonsCatalogFragment.GlassAddonsToolBarMediator);
    }
}
