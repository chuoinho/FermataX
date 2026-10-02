package me.aap.fermata.addon.web.tiktok;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TikTokWebSessionPolicyTest {

    @Test
    public void testHomeUrlAndEntry() {
        assertEquals("https://www.tiktok.com/foryou", TikTokWebSessionPolicy.HOME_URL);
        assertEquals("https://www.tiktok.com/foryou", TikTokWebSessionPolicy.entryUrl(true, null));
        assertEquals("https://www.tiktok.com/foryou", TikTokWebSessionPolicy.entryUrl(false, null));
        assertEquals("https://www.tiktok.com/@tiktok/video/123",
                TikTokWebSessionPolicy.entryUrl(false, "https://www.tiktok.com/@tiktok/video/123"));
        assertEquals("https://www.tiktok.com/foryou",
                TikTokWebSessionPolicy.entryUrl(false, "https://www.tiktok.com/login"));
    }

    @Test
    public void testHostedRoutes() {
        assertTrue(TikTokWebSessionPolicy.isHostedRoute("https://www.tiktok.com/foryou"));
        assertTrue(TikTokWebSessionPolicy.isHostedRoute("https://tiktok.com/@user/video/12345"));
        assertTrue(TikTokWebSessionPolicy.isHostedRoute("https://m.tiktok.com/v/123.html"));
        assertTrue(TikTokWebSessionPolicy.isHostedRoute("https://www.tiktok.com/login"));

        assertFalse(TikTokWebSessionPolicy.isHostedRoute("http://www.tiktok.com/foryou")); // plain http
        assertFalse(TikTokWebSessionPolicy.isHostedRoute("https://attacker.com/foryou"));
        assertFalse(TikTokWebSessionPolicy.isHostedRoute("https://www.youtube.com"));
        assertFalse(TikTokWebSessionPolicy.isHostedRoute(null));
        assertFalse(TikTokWebSessionPolicy.isHostedRoute(""));
    }

    @Test
    public void testPersistableRoutes() {
        assertTrue(TikTokWebSessionPolicy.isPersistableRoute("https://www.tiktok.com/foryou"));
        assertTrue(TikTokWebSessionPolicy.isPersistableRoute("https://www.tiktok.com/@creator/video/999"));
        assertFalse(TikTokWebSessionPolicy.isPersistableRoute("https://www.tiktok.com/login"));
        assertFalse(TikTokWebSessionPolicy.isPersistableRoute("https://www.tiktok.com/signup"));
        assertFalse(TikTokWebSessionPolicy.isPersistableRoute("https://google.com"));
    }

    @Test
    public void testBackNavigationHierarchy() {
        // At home route: backTarget is null (meaning exit to dashboard)
        assertNull(TikTokWebSessionPolicy.backTarget("https://www.tiktok.com/foryou"));
        assertNull(TikTokWebSessionPolicy.backTarget("https://www.tiktok.com/"));
        assertNull(TikTokWebSessionPolicy.backTarget("https://www.tiktok.com"));

        // Deeper in sub-pages: backTarget returns HOME_URL
        assertEquals(TikTokWebSessionPolicy.HOME_URL,
                TikTokWebSessionPolicy.backTarget("https://www.tiktok.com/@creator/video/123"));
        assertEquals(TikTokWebSessionPolicy.HOME_URL,
                TikTokWebSessionPolicy.backTarget("https://www.tiktok.com/search?q=music"));
    }

    @Test
    public void testSearchUrl() {
        assertEquals("https://www.tiktok.com/search?q=car+music",
                TikTokWebSessionPolicy.searchUrl("car music"));
        assertEquals(TikTokWebSessionPolicy.HOME_URL, TikTokWebSessionPolicy.searchUrl(""));
        assertEquals(TikTokWebSessionPolicy.HOME_URL, TikTokWebSessionPolicy.searchUrl(null));
    }
}
