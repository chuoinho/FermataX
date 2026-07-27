package me.aap.fermata.addon.stremio.subtitle;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SubtitleLanguageNormalizerTest {
	@Test
	public void normalizesIsoAliasesAndBcp47WithoutLosingScriptOrRegion() {
		assertEquals("en", SubtitleLanguageNormalizer.normalize("eng").tag());
		assertEquals("fr-CA", SubtitleLanguageNormalizer.normalize("fre_CA").tag());
		assertEquals("zh-Hant-TW", SubtitleLanguageNormalizer.normalize("chi-Hant-TW").tag());
		assertEquals("ja", SubtitleLanguageNormalizer.normalize("jpn").tag());
	}

	@Test
	public void identifiesCjkAndRtlLanguages() {
		assertEquals(SubtitleLanguage.Direction.LTR,
				SubtitleLanguageNormalizer.normalize("zh-Hans").direction());
		assertEquals(SubtitleLanguage.Direction.RTL,
				SubtitleLanguageNormalizer.normalize("ara-EG").direction());
		assertEquals(SubtitleLanguage.Direction.RTL,
				SubtitleLanguageNormalizer.normalize("iw-IL").direction());
		assertEquals("und", SubtitleLanguageNormalizer.normalize("unknown language").tag());
	}
}
