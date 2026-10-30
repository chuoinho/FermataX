package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class RadioBrowserStationTest {

	@Test
	public void hasFaviconUsesLocaleRoot() {
		Locale defaultLocale = Locale.getDefault();
		try {
			// Turkish locale where 'I' lowercases to 'ı' (dotless i), which would break "HTTP://".toLowerCase() without Locale.ROOT
			Locale.setDefault(Locale.forLanguageTag("tr-TR"));

			RadioBrowserStation stationWithHttps = new RadioBrowserStation("uuid-1", "Station",
					"HTTP://STREAM.TEST/LIVE", "", "", "HTTPS://EXAMPLE.TEST/FAVICON.PNG", "",
					"", "", "", "", 128, 0, 0);

			assertTrue("hasFavicon should be true for uppercase HTTPS URL in any locale",
					stationWithHttps.hasFavicon());
			assertTrue("hasStreamUrl should be true for uppercase HTTP URL in any locale",
					stationWithHttps.hasStreamUrl());

			RadioBrowserStation stationWithoutFavicon = new RadioBrowserStation("uuid-2", "Station",
					"HTTP://STREAM.TEST/LIVE", "", "", "ftp://example.test/icon.png", "",
					"", "", "", "", 128, 0, 0);

			assertFalse(stationWithoutFavicon.hasFavicon());
		} finally {
			Locale.setDefault(defaultLocale);
		}
	}
}
