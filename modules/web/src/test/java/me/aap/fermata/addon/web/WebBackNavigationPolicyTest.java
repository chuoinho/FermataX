package me.aap.fermata.addon.web;

import static me.aap.fermata.addon.web.WebBackNavigationPolicy.Action.EXIT_FULLSCREEN;
import static me.aap.fermata.addon.web.WebBackNavigationPolicy.Action.PARENT;
import static me.aap.fermata.addon.web.WebBackNavigationPolicy.Action.WEB_HISTORY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WebBackNavigationPolicyTest {
	@Test
	public void fullscreenExitAlwaysPrecedesWebHistory() {
		assertEquals(EXIT_FULLSCREEN, WebBackNavigationPolicy.resolve(true, true));
		assertEquals(EXIT_FULLSCREEN, WebBackNavigationPolicy.resolve(true, false));
		assertEquals(WEB_HISTORY, WebBackNavigationPolicy.resolve(false, true));
		assertEquals(PARENT, WebBackNavigationPolicy.resolve(false, false));
	}
}
