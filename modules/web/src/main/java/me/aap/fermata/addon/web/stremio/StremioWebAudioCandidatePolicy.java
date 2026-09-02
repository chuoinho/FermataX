package me.aap.fermata.addon.web.stremio;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Pure, conservative policy shared by the WebAudio bridge and its JVM tests. The JavaScript
 * selector applies the same gates to live DOM elements; this type deliberately contains no DOM
 * or playback data.
 */
final class StremioWebAudioCandidatePolicy {
	private StremioWebAudioCandidatePolicy() {
	}

	enum SourceKind {
		BLOB_MSE,
		DIRECT_HTTP,
		DIRECT_HTTPS,
		OTHER
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

	/**
	 * A source node can be created only once, so an attached graph survives a normal pause or a
	 * transient buffering stall. It is released only when ownership of that source generation ends.
	 */
	static boolean retainsAttachedOwner(Candidate candidate, boolean sourceUnchanged,
			boolean mediaError) {
		return candidate.mainDocument && candidate.connected && !candidate.ended &&
				!mediaError && sourceUnchanged && !candidate.mediaKeysPresent &&
				!candidate.encryptedObserved && (candidate.source == SourceKind.BLOB_MSE);
	}

	/** Returns one clear winner only; tied eligible candidates fail closed. */
	@Nullable
	static Candidate select(List<Candidate> candidates) {
		Candidate winner = null;
		for (Candidate candidate : candidates) {
			if (!isEligible(candidate)) continue;
			if (winner == null) {
				winner = candidate;
			} else if (candidate.activityRank == winner.activityRank) {
				return null;
			} else if (candidate.activityRank > winner.activityRank) {
				winner = candidate;
			}
		}
		return winner;
	}

	static boolean sourceChanged(@Nullable String attachedSource, @Nullable String currentSource) {
		return (attachedSource == null) ? (currentSource != null) :
				!attachedSource.equals(currentSource);
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
		final long activityRank;

		Candidate(boolean mainDocument, boolean connected, boolean visible, boolean advancing,
				boolean paused, boolean ended, int readyState, boolean mediaKeysPresent,
				boolean encryptedObserved, SourceKind source, long activityRank) {
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
			this.activityRank = activityRank;
		}
	}
}
