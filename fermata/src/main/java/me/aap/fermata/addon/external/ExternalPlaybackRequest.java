package me.aap.fermata.addon.external;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.net.URISyntaxException;

/** Immutable metadata and target for an addon-owned external playback handoff. */
public final class ExternalPlaybackRequest {
	private final String contentId;
	private final String title;
	private final String artworkUri;
	private final long durationMillis;
	private final ExternalPlaybackTargetKind targetKind;
	private final String target;
	private final ExternalNavigationPolicy navigationPolicy;

	public ExternalPlaybackRequest(String contentId, String title, String artworkUri,
			long durationMillis, ExternalPlaybackTargetKind targetKind, String target) {
		this(contentId, title, artworkUri, durationMillis, targetKind, target, null);
	}

	public ExternalPlaybackRequest(String contentId, String title, String artworkUri,
			long durationMillis, ExternalPlaybackTargetKind targetKind, String target,
			ExternalNavigationPolicy navigationPolicy) {
		this.contentId = requireText(contentId, "contentId");
		this.title = requireNonNull(title, "title");
		this.artworkUri = requireNonNull(artworkUri, "artworkUri");
		if (durationMillis < 0L) throw new IllegalArgumentException("Negative duration");
		this.durationMillis = durationMillis;
		this.targetKind = requireNonNull(targetKind, "targetKind");
		this.target = validateTarget(targetKind, target);
		if ((targetKind == ExternalPlaybackTargetKind.EXTERNAL_HTTP) &&
				(navigationPolicy == null)) {
			throw new IllegalArgumentException("External HTTP target requires a navigation policy");
		}
		if ((targetKind != ExternalPlaybackTargetKind.EXTERNAL_HTTP) &&
				(navigationPolicy != null)) {
			throw new IllegalArgumentException("Navigation policy is only valid for external HTTP");
		}
		this.navigationPolicy = navigationPolicy;
	}

	public String getContentId() {
		return contentId;
	}

	public String getTitle() {
		return title;
	}

	public String getArtworkUri() {
		return artworkUri;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public ExternalPlaybackTargetKind getTargetKind() {
		return targetKind;
	}

	public String getTarget() {
		return target;
	}

	public ExternalNavigationPolicy getNavigationPolicy() {
		return navigationPolicy;
	}

	/** Releases the transient policy lease. Safe to call more than once when policy is idempotent. */
	public void close() {
		if (navigationPolicy != null) navigationPolicy.close();
	}

	@Override
	public String toString() {
		return "ExternalPlaybackRequest{" +
				"targetKind=" + targetKind +
				", contentIdPresent=true" +
				", titlePresent=" + !title.isEmpty() +
				", artworkPresent=" + !artworkUri.isEmpty() +
				", durationMillis=" + durationMillis +
				'}';
	}

	private static String validateTarget(ExternalPlaybackTargetKind kind, String value) {
		String target = requireText(value, "target");
		if (kind == ExternalPlaybackTargetKind.YOUTUBE_ID) {
			for (int i = 0; i < target.length(); i++) {
				char c = target.charAt(i);
				if (!Character.isLetterOrDigit(c) && (c != '-') && (c != '_'))
					throw new IllegalArgumentException("Invalid YouTube target");
			}
			return target;
		}

		try {
			URI uri = new URI(target);
			String scheme = uri.getScheme();
			if ((uri.getHost() == null) ||
					(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)))
				throw new IllegalArgumentException("External target must be HTTP(S)");
			return uri.toASCIIString();
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException("Invalid external target", ex);
		}
	}

	private static String requireText(String value, String name) {
		String text = requireNonNull(value, name).trim();
		if (text.isEmpty()) throw new IllegalArgumentException(name + " is empty");
		return text;
	}
}
