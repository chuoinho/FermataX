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
}
