package me.aap.fermata.addon.web.stremio;

/**
 * Embedded styling and scripts for Stremio Web player.
 * Hides Stremio's branding logo in the Web addon so it does not collide
 * or overlap with FermataX's native automotive back button.
 */
public final class StremioScripts {

	public static final String STREMIO_CLEAN_CSS = """
			[class*="logo-pill-symbol"],
			[class*="wireframe-pill-logo"],
			[class*="horizontal-nav-bar-container"] [class*="logo-container"],
			[class*="horizontal-nav-bar-container"] img[src*="stremio_symbol"],
			[class*="logo-container"] > [class*="logo"],
			img[src*="stremio_symbol"] {
				visibility: hidden !important;
				opacity: 0 !important;
				pointer-events: none !important;
			}
			""";

	public static final String STREMIO_CLEAN_JS = """
			(function () {
			  if (window.__fermataStremioClean) { return; }
			  window.__fermataStremioClean = true;

			  var CSS = '[class*="logo-pill-symbol"], ' +
			    '[class*="wireframe-pill-logo"], ' +
			    '[class*="horizontal-nav-bar-container"] [class*="logo-container"], ' +
			    '[class*="horizontal-nav-bar-container"] img[src*="stremio_symbol"], ' +
			    '[class*="logo-container"] > [class*="logo"], ' +
			    'img[src*="stremio_symbol"] ' +
			    '{ visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; }';

			  function inject() {
			    var head = document.head || document.documentElement;
			    if (!head) {
			      setTimeout(inject, 50);
			      return;
			    }
			    if (document.getElementById('__fermata_stremio_clean')) { return; }
			    var style = document.createElement('style');
			    style.id = '__fermata_stremio_clean';
			    style.textContent = CSS;
			    head.appendChild(style);
			  }

			  inject();
			  if (document.readyState === 'loading') {
			    document.addEventListener('DOMContentLoaded', inject);
			  }
			})();
			""";

	public static String injectSource() {
		return STREMIO_CLEAN_JS;
	}

	private StremioScripts() {
	}
}
