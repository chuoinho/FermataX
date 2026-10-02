package me.aap.fermata.addon.web.tiktok;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TikTokScriptsTest {

    @Test
    public void testCleanScriptIntegrity() {
        String clean = TikTokScripts.TT_CLEAN;
        assertNotNull(clean);
        assertFalse(clean.trim().isEmpty());
        assertTrue("Must contain __cntTtClean", clean.contains("__cntTtClean"));
        assertTrue("Must contain recommend-list-item-container",
                clean.contains("recommend-list-item-container"));
        assertTrue("Must contain feed-video", clean.contains("feed-video"));
        assertTrue("Must contain object-fit:contain", clean.contains("object-fit:contain"));
        assertTrue("Must protect photo swiper", clean.contains("swiper img") || clean.contains(".swiper"));
        assertTrue("Must contain __cnt_tt_clean style id", clean.contains("__cnt_tt_clean"));
    }

    @Test
    public void testNavScriptIntegrity() {
        String nav = TikTokScripts.TT_NAV;
        assertNotNull(nav);
        assertFalse(nav.trim().isEmpty());
        assertTrue("Must contain __cntTtNav", nav.contains("__cntTtNav"));
        assertTrue("Must expose __cntNext", nav.contains("window.__cntNext"));
        assertTrue("Must expose __cntPrev", nav.contains("window.__cntPrev"));
        assertTrue("Must expose __cntVideo", nav.contains("window.__cntVideo"));
        assertTrue("Must expose __cntTitle", nav.contains("window.__cntTitle"));
        assertTrue("Must contain feed-navigation-next", cleanNav().contains("feed-navigation-next"));
        assertTrue("Must contain WheelEvent fallback", cleanNav().contains("WheelEvent"));
        assertTrue("Must contain ArrowDown fallback", cleanNav().contains("ArrowDown"));
    }

    @Test
    public void testPlayStateScriptIntegrity() {
        String playState = TikTokScripts.PLAY_STATE;
        assertNotNull(playState);
        assertFalse(playState.trim().isEmpty());
        assertTrue("Must contain __cntPlayState", playState.contains("__cntPlayState"));
        assertTrue("Must report to CntBridge", playState.contains("CntBridge.setPlaying"));
        assertTrue("Must support __cntSeek", playState.contains("__cntSeek"));
    }

    @Test
    public void testCombinedInjectionScript() {
        String combined = TikTokScripts.injectSource();
        assertNotNull(combined);
        assertTrue(combined.contains("__cntTtClean"));
        assertTrue(combined.contains("__cntTtNav"));
        assertTrue(combined.contains("__cntPlayState"));
    }

    private String cleanNav() {
        return TikTokScripts.TT_NAV;
    }
}
