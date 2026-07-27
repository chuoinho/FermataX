package me.aap.fermata.addon.stremio.ui.source;

/** Explicit network permissions granted to one Stremio provider. */
public record SourceUiConsent(boolean allowCleartext, boolean allowLan) {
	public static final SourceUiConsent STRICT = new SourceUiConsent(false, false);
}
