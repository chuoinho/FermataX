package me.aap.fermata.addon.web.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.AddonCapability;

public class TikTokAddonRegistrationTest {

    @Test
    public void testCapabilityRegistered() {
        assertNotNull(AddonCapability.valueOf("TIKTOK"));
        assertEquals(AddonCapability.TIKTOK, AddonCapability.valueOf("TIKTOK"));
    }

    @Test
    public void testResourceIdentifiersExist() {
        assertTrue(me.aap.fermata.addon.web.R.string.addon_tiktok != 0);
        assertTrue(me.aap.fermata.addon.web.R.string.tiktok_for_you != 0);
        assertTrue(me.aap.fermata.addon.web.R.drawable.ic_app_tiktok != 0);
        assertTrue(me.aap.fermata.R.id.tiktok_fragment != 0);
    }
}
