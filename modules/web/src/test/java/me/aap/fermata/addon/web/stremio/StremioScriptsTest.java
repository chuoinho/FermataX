package me.aap.fermata.addon.web.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StremioScriptsTest {

	@Test
	public void testStremioCleanScriptIntegrity() {
		String css = StremioScripts.STREMIO_CLEAN_CSS;
		assertNotNull("STREMIO_CLEAN_CSS must not be null", css);
		assertFalse("STREMIO_CLEAN_CSS must not be empty", css.trim().isEmpty());

		assertTrue("Must hide logo-pill-symbol", css.contains("[class*=\"logo-pill-symbol\"]"));
		assertTrue("Must hide wireframe-pill-logo", css.contains("[class*=\"wireframe-pill-logo\"]"));
		assertTrue("Must hide horizontal-nav-bar logo-container",
				css.contains("[class*=\"horizontal-nav-bar-container\"] [class*=\"logo-container\"]"));
		assertTrue("Must hide horizontal-nav-bar stremio_symbol",
				css.contains("[class*=\"horizontal-nav-bar-container\"] img[src*=\"stremio_symbol\"]"));
		assertTrue("Must hide generic logo-container logo",
				css.contains("[class*=\"logo-container\"] > [class*=\"logo\"]"));
		assertTrue("Must hide stremio_symbol image", css.contains("img[src*=\"stremio_symbol\"]"));

		String js = StremioScripts.injectSource();
		assertNotNull("injectSource must not be null", js);
		assertTrue("Must contain guard __fermataStremioClean", js.contains("__fermataStremioClean"));
		assertTrue("Must create style element __fermata_stremio_clean", js.contains("__fermata_stremio_clean"));
		assertTrue("Must contain inject logic", js.contains("inject()"));

		// Ensure there are no unescaped syntax errors breaking the script
		assertFalse("Must not have unescaped quote breaks", js.contains("style.textContent = \"\""));
		assertTrue("Must assign CSS textContent", js.contains("style.textContent = CSS"));
	}
}
