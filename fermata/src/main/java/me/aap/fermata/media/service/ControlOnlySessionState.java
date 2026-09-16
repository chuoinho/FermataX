package me.aap.fermata.media.service;

/** Single authority for the short-lived owner and generation of control-only playback. */
public final class ControlOnlySessionState {
	private long epoch;
	private MediaSessionCallback.ControlOnlyDelegate owner;
	private String key = "";
	private ControlOnlyPresentation presentation;

	public long claim(MediaSessionCallback.ControlOnlyDelegate next, String contentKey) {
		if (next == null || (owner != null && owner != next)) return 0L;
		String value = (contentKey == null) ? "" : contentKey;
		if (owner == null || !key.equals(value)) {
			++epoch;
			owner = next;
			key = value;
		}
		return epoch;
	}

	public ControlOnlyPresentation claimPresentation(MediaSessionCallback.ControlOnlyDelegate next,
			String contentKey, int state, long actions, android.support.v4.media.MediaMetadataCompat metadata) {
		String addon = next.controlOnlyAddonClass();
		if (addon == null || addon.isBlank()) return null;
		long id = claim(next, contentKey);
		if (id <= 0L) return null;
		publish(id, addon, state, next.controlOnlyActions() | actions, metadata);
		return presentation;
	}

	public boolean isCurrent(MediaSessionCallback.ControlOnlyDelegate expected, long id) {
		return expected != null && owner == expected && id > 0L && id == epoch;
	}

	public boolean isCurrent(ControlOnlyPresentation value, MediaSessionCallback.ControlOnlyDelegate expected) {
		return value != null && isCurrent(expected, value.leaseId());
	}

	public static boolean supports(long actions, MediaSessionCallback.ControlOnlyAction action) {
		return action != null && switch (action) { case PLAY -> (actions & android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY) != 0;
			case PAUSE -> (actions & android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE) != 0;
			case NEXT_TRACK -> (actions & android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0;
			case PREVIOUS_TRACK -> (actions & android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0; };
	}

	public boolean release(MediaSessionCallback.ControlOnlyDelegate expected) {
		if (owner == null || owner != expected) return false;
		invalidate();
		return true;
	}

	public void publish(long leaseId, String addonClass, int state, long actions,
			android.support.v4.media.MediaMetadataCompat metadata) {
		presentation = new ControlOnlyPresentation(leaseId, addonClass, state, actions, metadata);
	}

	public ControlOnlyPresentation presentation() {
		return presentation;
	}

	public MediaSessionCallback.ControlOnlyDelegate invalidateAndGetOwner() {
		MediaSessionCallback.ControlOnlyDelegate previous = owner;
		invalidate();
		return previous;
	}

	public MediaSessionCallback.ControlOnlyDelegate owner() {
		return owner;
	}

	public long epoch() {
		return epoch;
	}

	public void invalidate() {
		++epoch;
		owner = null;
		key = "";
		presentation = null;
	}
}
