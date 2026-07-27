package me.aap.fermata.addon.stremio.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SourceFormValidatorTest {
	@Test
	public void acceptsHttpsAndStremioWithoutBroadConsent() {
		assertEquals(SourceUiError.NONE, validate("https://example.com/manifest.json",
				SourceUiConsent.STRICT));
		assertEquals(SourceUiError.NONE, validate("stremio://example.com/manifest.json",
				SourceUiConsent.STRICT));
	}

	@Test
	public void cleartextAndPrivateLiteralRequireSeparateExplicitConsent() {
		assertEquals(SourceUiError.CLEARTEXT_CONSENT_REQUIRED,
				validate("http://192.168.1.20/manifest.json", SourceUiConsent.STRICT));
		assertEquals(SourceUiError.LAN_CONSENT_REQUIRED,
				validate("http://192.168.1.20/manifest.json",
						new SourceUiConsent(true, false)));
		assertEquals(SourceUiError.NONE,
				validate("http://192.168.1.20/manifest.json",
						new SourceUiConsent(true, true)));
	}

	@Test
	public void rejectsUnsupportedMalformedAndUserInfoUrls() {
		assertEquals(SourceUiError.INVALID_URL, validate("", SourceUiConsent.STRICT));
		assertEquals(SourceUiError.INVALID_URL,
				validate("file:///private/source", SourceUiConsent.STRICT));
		assertEquals(SourceUiError.INVALID_URL,
				validate("https://user:password@example.com/manifest.json",
						SourceUiConsent.STRICT));
	}

	@Test
	public void sensitiveModelsNeverRenderUrlTokenOrProviderName() {
		String secret = "do-not-render-token";
		SourceUiDraft draft = new SourceUiDraft(
				"https://example.com/" + secret + "/manifest.json", secret,
				SourceUiConsent.STRICT);
		SourceUiItem item = new SourceUiItem("source-id", secret, "1.0",
				"https://example.com/" + secret, true, 0, null, false,
				SourceUiConsent.STRICT);
		assertFalse(draft.toString().contains(secret));
		assertFalse(item.toString().contains(secret));
		assertFalse(new SourceUiSnapshot(1, java.util.List.of(item)).toString().contains(secret));
	}

	private static SourceUiError validate(String url, SourceUiConsent consent) {
		return SourceFormValidator.validate(new SourceUiDraft(url, "hidden", consent));
	}
}
