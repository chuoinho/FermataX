package me.aap.fermata.addon.stremio.net;

public record NetworkConsent(boolean allowCleartext, boolean allowLan) {
	public static final NetworkConsent STRICT = new NetworkConsent(false, false);
}
