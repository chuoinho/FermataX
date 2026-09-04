package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Conservative, DOM-free policy mirrored by the YouTube document-start script. */
final class YoutubeWebAudioCandidatePolicy {
	private YoutubeWebAudioCandidatePolicy() {
	}

	enum SourceKind {
		BLOB_MSE, DIRECT_HTTP, DIRECT_HTTPS, OTHER
	}

	static SourceKind classifySource(@Nullable String source) {
		if (source == null) return SourceKind.OTHER;
		String value = source.toLowerCase(Locale.ROOT);
		if (value.startsWith("blob:")) return SourceKind.BLOB_MSE;
		if (value.startsWith("http:")) return SourceKind.DIRECT_HTTP;
		if (value.startsWith("https:")) return SourceKind.DIRECT_HTTPS;
		return SourceKind.OTHER;
	}

	static boolean isEligible(Candidate candidate) {
		return candidate.mainDocument && candidate.connected && candidate.visible &&
				candidate.advancing && !candidate.paused && !candidate.ended &&
				(candidate.readyState >= 2) && !candidate.mediaKeysPresent &&
				!candidate.encryptedObserved && (candidate.source == SourceKind.BLOB_MSE);
	}

	/** A claimed element remains claimable across its next Blob/MSE clip without a second source. */
	static boolean reusesClaimedElement(@Nullable String oldSource, @Nullable String newSource) {
		return (classifySource(oldSource) == SourceKind.BLOB_MSE) &&
				(classifySource(newSource) == SourceKind.BLOB_MSE);
	}

	static final class Candidate {
		final boolean mainDocument;
		final boolean connected;
		final boolean visible;
		final boolean advancing;
		final boolean paused;
		final boolean ended;
		final int readyState;
		final boolean mediaKeysPresent;
		final boolean encryptedObserved;
		final SourceKind source;

		Candidate(boolean mainDocument, boolean connected, boolean visible, boolean advancing,
				boolean paused, boolean ended, int readyState, boolean mediaKeysPresent,
				boolean encryptedObserved, SourceKind source) {
			this.mainDocument = mainDocument;
			this.connected = connected;
			this.visible = visible;
			this.advancing = advancing;
			this.paused = paused;
			this.ended = ended;
			this.readyState = readyState;
			this.mediaKeysPresent = mediaKeysPresent;
			this.encryptedObserved = encryptedObserved;
			this.source = source;
		}
	}
}
