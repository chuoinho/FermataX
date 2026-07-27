package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;

import me.aap.fermata.media.net.PlaybackEndpointValidator;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.addon.stremio.protocol.response.StreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.StremioSubtitle;
import java.util.List;
import java.util.LinkedHashSet;

/** Immutable, short-lived handoff descriptor. Raw targets never participate in durable identity. */
public final class PlaybackDescriptor {
	private final String descriptorId;
	private final String selectionFingerprint;
	private final StremioPlaybackIdentity identity;
	private final String providerSourceUuid;
	private final String providerName;
	private final String streamName;
	private final String streamTitle;
	private final String videoHash;
	private final Long videoSize;
	private final String filename;
	private final StremioPlaybackMetadata metadata;
	private final TargetKind targetKind;
	private final StreamTarget sourceTarget;
	private final List<StremioSubtitle> embeddedSubtitles;
	private final String targetValue;
	private final PlaybackRequestProfile requestProfile;
	private final PlaybackEndpointValidator endpointValidator;
	private final UnsupportedReason unsupportedReason;
	private final long createdAtEpochMillis;
	private final long expiresAtEpochMillis;
	private final StreamProvider providerSnapshot;
	private final List<String> contributingProviderSourceUuids;

	PlaybackDescriptor(String descriptorId, String selectionFingerprint,
			StremioPlaybackIdentity identity,
			String providerSourceUuid, String providerName, String streamName, String streamTitle,
			StremioPlaybackMetadata metadata, TargetKind targetKind, String targetValue,
			PlaybackRequestProfile requestProfile, PlaybackEndpointValidator endpointValidator,
			UnsupportedReason unsupportedReason,
			long createdAtEpochMillis, long expiresAtEpochMillis,
			StreamProvider providerSnapshot) {
		this(descriptorId, selectionFingerprint, identity, providerSourceUuid, providerName,
				streamName, streamTitle, metadata, targetKind, null, List.of(), targetValue, requestProfile,
				endpointValidator, unsupportedReason, createdAtEpochMillis, expiresAtEpochMillis,
				providerSnapshot);
	}

	PlaybackDescriptor(String descriptorId, String selectionFingerprint,
			StremioPlaybackIdentity identity,
			String providerSourceUuid, String providerName, String streamName, String streamTitle,
			StremioPlaybackMetadata metadata, TargetKind targetKind, StreamTarget sourceTarget,
			List<StremioSubtitle> embeddedSubtitles,
			String targetValue, PlaybackRequestProfile requestProfile,
			PlaybackEndpointValidator endpointValidator, UnsupportedReason unsupportedReason,
			long createdAtEpochMillis, long expiresAtEpochMillis,
			StreamProvider providerSnapshot) {
		this(descriptorId, selectionFingerprint, identity, providerSourceUuid, providerName,
				streamName, streamTitle, null, null, null, metadata, targetKind, sourceTarget,
				embeddedSubtitles, targetValue, requestProfile, endpointValidator, unsupportedReason,
				createdAtEpochMillis, expiresAtEpochMillis, providerSnapshot);
	}

	PlaybackDescriptor(String descriptorId, String selectionFingerprint,
			StremioPlaybackIdentity identity,
			String providerSourceUuid, String providerName, String streamName, String streamTitle,
			String videoHash, Long videoSize, String filename,
			StremioPlaybackMetadata metadata, TargetKind targetKind, StreamTarget sourceTarget,
			List<StremioSubtitle> embeddedSubtitles,
			String targetValue, PlaybackRequestProfile requestProfile,
			PlaybackEndpointValidator endpointValidator, UnsupportedReason unsupportedReason,
			long createdAtEpochMillis, long expiresAtEpochMillis,
			StreamProvider providerSnapshot) {
		this(descriptorId, selectionFingerprint, identity, providerSourceUuid, providerName,
				streamName, streamTitle, videoHash, videoSize, filename, metadata, targetKind,
				sourceTarget, embeddedSubtitles, targetValue, requestProfile, endpointValidator,
				unsupportedReason, createdAtEpochMillis, expiresAtEpochMillis, providerSnapshot,
				true);
	}

	private PlaybackDescriptor(String descriptorId, String selectionFingerprint,
			StremioPlaybackIdentity identity,
			String providerSourceUuid, String providerName, String streamName, String streamTitle,
			String videoHash, Long videoSize, String filename,
			StremioPlaybackMetadata metadata, TargetKind targetKind, StreamTarget sourceTarget,
			List<StremioSubtitle> embeddedSubtitles,
			String targetValue, PlaybackRequestProfile requestProfile,
			PlaybackEndpointValidator endpointValidator, UnsupportedReason unsupportedReason,
			long createdAtEpochMillis, long expiresAtEpochMillis,
			StreamProvider providerSnapshot, boolean ignored) {
		this(descriptorId, selectionFingerprint, identity, providerSourceUuid, providerName,
				streamName, streamTitle, videoHash, videoSize, filename, metadata, targetKind,
				sourceTarget, embeddedSubtitles, targetValue, requestProfile, endpointValidator,
				unsupportedReason, createdAtEpochMillis, expiresAtEpochMillis, providerSnapshot,
				List.of(providerSourceUuid), ignored);
	}

	private PlaybackDescriptor(String descriptorId, String selectionFingerprint,
			StremioPlaybackIdentity identity,
			String providerSourceUuid, String providerName, String streamName, String streamTitle,
			String videoHash, Long videoSize, String filename,
			StremioPlaybackMetadata metadata, TargetKind targetKind, StreamTarget sourceTarget,
			List<StremioSubtitle> embeddedSubtitles,
			String targetValue, PlaybackRequestProfile requestProfile,
			PlaybackEndpointValidator endpointValidator, UnsupportedReason unsupportedReason,
			long createdAtEpochMillis, long expiresAtEpochMillis,
			StreamProvider providerSnapshot, List<String> contributors, boolean ignored) {
		this.descriptorId = StremioPlaybackIdentity.requireText(descriptorId, "descriptorId");
		this.selectionFingerprint = StremioPlaybackIdentity.requireText(
				selectionFingerprint, "selectionFingerprint");
		this.identity = Objects.requireNonNull(identity, "identity");
		this.providerSourceUuid = StremioPlaybackIdentity.requireText(
				providerSourceUuid, "providerSourceUuid");
		this.providerName = Objects.requireNonNull(providerName, "providerName");
		this.streamName = streamName;
		this.streamTitle = streamTitle;
		this.videoHash = videoHash;
		this.videoSize = videoSize;
		this.filename = filename;
		this.metadata = Objects.requireNonNull(metadata, "metadata");
		this.targetKind = Objects.requireNonNull(targetKind, "targetKind");
		this.sourceTarget = sourceTarget;
		this.embeddedSubtitles = List.copyOf(Objects.requireNonNull(
				embeddedSubtitles, "embeddedSubtitles"));
		this.targetValue = targetValue;
		this.requestProfile = requestProfile;
		this.endpointValidator = endpointValidator;
		this.unsupportedReason = unsupportedReason;
		this.createdAtEpochMillis = createdAtEpochMillis;
		this.expiresAtEpochMillis = expiresAtEpochMillis;
		this.providerSnapshot = Objects.requireNonNull(providerSnapshot, "providerSnapshot");
		this.contributingProviderSourceUuids = List.copyOf(
				Objects.requireNonNull(contributors, "contributors"));
		if (contributingProviderSourceUuids.isEmpty()) {
			throw new IllegalArgumentException("descriptor must retain a contributing provider");
		}
		if (expiresAtEpochMillis <= createdAtEpochMillis) {
			throw new IllegalArgumentException("descriptor expiry must follow creation");
		}
	}

	public String descriptorId() {
		return descriptorId;
	}

	/** Stable semantic choice identity; unlike descriptorId it excludes rotating target URLs. */
	public String selectionFingerprint() {
		return selectionFingerprint;
	}

	public StremioPlaybackIdentity identity() {
		return identity;
	}

	public String providerSourceUuid() {
		return providerSourceUuid;
	}

	public String providerName() {
		return providerName;
	}

	public String streamName() {
		return streamName;
	}

	public String streamTitle() {
		return streamTitle;
	}

	public String videoHash() {
		return videoHash;
	}

	public Long videoSize() {
		return videoSize;
	}

	public String filename() {
		return filename;
	}

	public StremioPlaybackMetadata metadata() {
		return metadata;
	}

	public TargetKind targetKind() {
		return targetKind;
	}

	/** Original in-memory protocol target. Its string form is always redacted. */
	public StreamTarget sourceTarget() {
		return sourceTarget;
	}

	public List<StremioSubtitle> embeddedSubtitles() {
		return embeddedSubtitles;
	}

	/** Direct or external URL. Callers must never persist this value. */
	public String targetValue() {
		return targetValue;
	}

	public PlaybackRequestProfile requestProfile() {
		return requestProfile;
	}

	public PlaybackEndpointValidator endpointValidator() {
		return endpointValidator;
	}

	public UnsupportedReason unsupportedReason() {
		return unsupportedReason;
	}

	public long createdAtEpochMillis() {
		return createdAtEpochMillis;
	}

	public long expiresAtEpochMillis() {
		return expiresAtEpochMillis;
	}

	public StreamProvider providerSnapshot() {
		return providerSnapshot;
	}

	public List<String> contributingProviderSourceUuids() {
		return contributingProviderSourceUuids;
	}

	PlaybackDescriptor mergeTorrentSources(PlaybackDescriptor other) {
		Objects.requireNonNull(other, "other");
		if (!(sourceTarget instanceof me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget left) ||
				!(other.sourceTarget instanceof me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget right) ||
				!left.infoHash().equalsIgnoreCase(right.infoHash()) ||
				!Objects.equals(left.fileIndex(), right.fileIndex())) return this;
		LinkedHashSet<String> sources = new LinkedHashSet<>(left.sources());
		sources.addAll(right.sources());
		LinkedHashSet<String> contributors = new LinkedHashSet<>(contributingProviderSourceUuids);
		contributors.addAll(other.contributingProviderSourceUuids);
		var mergedTarget = new me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget(
				left.infoHash(), left.fileIndex(), List.copyOf(sources));
		return new PlaybackDescriptor(descriptorId, selectionFingerprint, identity,
				providerSourceUuid, providerName, streamName, streamTitle, videoHash, videoSize,
				filename, metadata, targetKind, mergedTarget, embeddedSubtitles, targetValue,
				requestProfile, endpointValidator, unsupportedReason, createdAtEpochMillis,
				expiresAtEpochMillis, providerSnapshot, List.copyOf(contributors), true);
	}

	public boolean isExpired(long nowEpochMillis) {
		return nowEpochMillis >= expiresAtEpochMillis;
	}

	public DescriptorRefreshRequest refreshRequest() {
		return new DescriptorRefreshRequest(identity, providerSourceUuid,
				selectionFingerprint, descriptorId);
	}

	public void requireFresh(long nowEpochMillis) throws ExpiredPlaybackDescriptorException {
		if (isExpired(nowEpochMillis)) {
			throw new ExpiredPlaybackDescriptorException(refreshRequest());
		}
	}

	@Override
	public String toString() {
		return "PlaybackDescriptor{id=" + descriptorId + ", identity=" + identity +
				", target=" + targetKind + ", metadata=" + metadata +
				", expires=" + expiresAtEpochMillis + '}';
	}

	public enum TargetKind {
		DIRECT_HTTP,
		HLS,
		DASH,
		TORRENT,
		EXTERNAL,
		USENET,
		ARCHIVE,
		UNSUPPORTED
	}

	public enum UnsupportedReason {
		INFO_HASH_HANDLER_UNAVAILABLE,
		EXTERNAL_URL_HANDLER_UNAVAILABLE,
		USENET_HANDLER_UNAVAILABLE,
		ARCHIVE_HANDLER_UNAVAILABLE,
		YOUTUBE_TARGET_DISABLED,
		MISSING_TARGET,
		MULTIPLE_TARGETS,
		INVALID_TARGET,
		UNSUPPORTED_DIRECT_SCHEME,
		NETWORK_POLICY_REJECTED
	}

	public record DescriptorRefreshRequest(
			StremioPlaybackIdentity identity, String providerSourceUuid,
			String selectionFingerprint, String previousDescriptorId) {
		public DescriptorRefreshRequest {
			Objects.requireNonNull(identity, "identity");
			StremioPlaybackIdentity.requireText(providerSourceUuid, "providerSourceUuid");
			StremioPlaybackIdentity.requireText(selectionFingerprint, "selectionFingerprint");
			StremioPlaybackIdentity.requireText(previousDescriptorId, "previousDescriptorId");
		}

		@Override
		public String toString() {
			return "DescriptorRefreshRequest{identity=" + identity + ", provider=<redacted>}";
		}
	}

	public static final class ExpiredPlaybackDescriptorException extends Exception {
		private final DescriptorRefreshRequest refreshRequest;

		ExpiredPlaybackDescriptorException(DescriptorRefreshRequest refreshRequest) {
			super("Stremio playback choice expired; stream re-fetch is required");
			this.refreshRequest = refreshRequest;
		}

		public DescriptorRefreshRequest refreshRequest() {
			return refreshRequest;
		}
	}
}
