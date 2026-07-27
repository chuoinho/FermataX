package me.aap.fermata.addon.stremio.playback;

import java.net.URI;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ExternalStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.protocol.response.UnsupportedStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.YoutubeStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.NzbStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ArchiveStreamTarget;
import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.NetworkPolicy;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.PlaybackEndpointValidator;
import me.aap.fermata.media.net.PlaybackRequestValidationException;
import me.aap.fermata.media.net.ValidatedPlaybackEndpoint;

public final class PlaybackDescriptorFactory {
	public static final long DEFAULT_TTL_MILLIS = 5 * 60_000L;
	public static final long MAX_TTL_MILLIS = 15 * 60_000L;
	private static final long NETWORK_DECISION_TTL_MILLIS = 1_000L;
	private static final int MAX_NETWORK_DECISIONS = 256;

	private final long ttlMillis;
	private final PlaybackHeaderRegistry headerRegistry;
	private final AddressResolver addressResolver;
	private final Map<NetworkDecisionKey, NetworkDecision> networkDecisions =
			new LinkedHashMap<>(32, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<NetworkDecisionKey, NetworkDecision> eldest) {
					return size() > MAX_NETWORK_DECISIONS;
				}
			};

	public PlaybackDescriptorFactory(PlaybackHeaderRegistry headerRegistry) {
		this(DEFAULT_TTL_MILLIS, headerRegistry, null);
	}

	public PlaybackDescriptorFactory(long ttlMillis, PlaybackHeaderRegistry headerRegistry) {
		this(ttlMillis, headerRegistry, null);
	}

	public PlaybackDescriptorFactory(PlaybackHeaderRegistry headerRegistry,
			AddressResolver addressResolver) {
		this(DEFAULT_TTL_MILLIS, headerRegistry, addressResolver);
	}

	public PlaybackDescriptorFactory(long ttlMillis, PlaybackHeaderRegistry headerRegistry,
			AddressResolver addressResolver) {
		if ((ttlMillis <= 0) || (ttlMillis > MAX_TTL_MILLIS)) {
			throw new IllegalArgumentException("ttlMillis must be between 1 and 900000");
		}
		this.ttlMillis = ttlMillis;
		this.headerRegistry = Objects.requireNonNull(headerRegistry, "headerRegistry");
		this.addressResolver = addressResolver;
	}

	public PlaybackDescriptor create(StreamAggregationRequest request, StreamProvider provider,
			StremioStream stream, long nowEpochMillis) {
		return candidate(request, provider, stream, nowEpochMillis).descriptor();
	}

	Candidate candidate(StreamAggregationRequest request, StreamProvider provider,
			StremioStream stream, long nowEpochMillis) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(stream, "stream");
		if (stream.target() instanceof YoutubeStreamTarget) {
			throw new IllegalArgumentException("Stremio YouTube targets are disabled");
		}
		long expiresAt = Math.addExact(nowEpochMillis, ttlMillis);
		String targetFingerprint = targetFingerprint(stream);
		String selectionFingerprint = selectionFingerprint(stream);
		String descriptorId = "stremio:choice:" + StremioPlaybackIdentity.digest(
				request.identity().videoKey(), provider.sourceUuid(), targetFingerprint);

		PlaybackDescriptor.TargetKind kind;
		PlaybackDescriptor.UnsupportedReason unsupported = null;
		String targetValue = null;
		PlaybackRequestProfile profile = null;
		PlaybackEndpointValidator endpointValidator = null;
		int rank;

		if (stream.target() instanceof DirectStreamTarget direct) {
			targetValue = direct.url();
			URI uri = parseHttpUri(targetValue);
			if (uri == null) {
				kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
				unsupported = PlaybackDescriptor.UnsupportedReason.UNSUPPORTED_DIRECT_SCHEME;
				targetValue = null;
				rank = 60;
			} else {
				try {
					if (!provider.sourceLease().isCurrent()) throw new IOException();
					if ((addressResolver != null) && !isNetworkAllowed(
							uri, provider.networkConsent(), nowEpochMillis)) throw new IOException();
					kind = directKind(uri);
					rank = switch (kind) {
						case HLS -> 0;
						case DASH -> 1;
						default -> 2;
					};
					PlaybackRequestProfile.Builder builder = PlaybackRequestProfile.builder(
							uri, descriptorId).expiresAt(expiresAt)
							.redirectPolicy(addressResolver == null ?
									PlaybackRequestProfile.RedirectPolicy.SAME_ORIGIN :
									PlaybackRequestProfile.RedirectPolicy.VALIDATED_ENDPOINTS);
					if (addressResolver != null) {
						builder.requireCapability(
								PlaybackRequestProfile.EngineCapability.ENDPOINT_VALIDATION);
						var consent = provider.networkConsent();
						endpointValidator = target -> validatePlaybackEndpoint(
								target, consent, provider.sourceLease());
					}
					Map<String, String> headers = stream.behaviorHints().proxyHeaders().request();
					if (!headers.isEmpty()) {
						builder.headerReference(Objects.requireNonNull(
								headerRegistry.register(provider.sourceUuid(), descriptorId,
										headers, expiresAt), "header reference"));
					}
					profile = builder.build();
				} catch (IOException rejected) {
					kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
					unsupported = PlaybackDescriptor.UnsupportedReason.NETWORK_POLICY_REJECTED;
					targetValue = null;
					rank = 60;
				}
			}
		} else if (stream.target() instanceof ExternalStreamTarget external) {
			URI uri = parseHttpUri(external.url());
			if (uri == null) {
				kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
				unsupported = PlaybackDescriptor.UnsupportedReason.INVALID_TARGET;
			} else {
				// A WebView resolves and fetches subresources independently after URL validation.
				// Keep externalUrl unavailable until every request can use a pinned transport.
				kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
				unsupported = PlaybackDescriptor.UnsupportedReason.EXTERNAL_URL_HANDLER_UNAVAILABLE;
				targetValue = null;
			}
			rank = 60;
		} else if (stream.target() instanceof InfoHashStreamTarget infoHash) {
			kind = PlaybackDescriptor.TargetKind.TORRENT;
			targetValue = null;
			rank = 10;
		} else if (stream.target() instanceof NzbStreamTarget) {
			kind = PlaybackDescriptor.TargetKind.USENET;
			unsupported = PlaybackDescriptor.UnsupportedReason.USENET_HANDLER_UNAVAILABLE;
			rank = 55;
		} else if (stream.target() instanceof ArchiveStreamTarget) {
			kind = PlaybackDescriptor.TargetKind.ARCHIVE;
			unsupported = PlaybackDescriptor.UnsupportedReason.ARCHIVE_HANDLER_UNAVAILABLE;
			rank = 55;
		} else if (stream.target() instanceof UnsupportedStreamTarget target) {
			kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
			unsupported = switch (target.reason()) {
				case MISSING_TARGET -> PlaybackDescriptor.UnsupportedReason.MISSING_TARGET;
				case MULTIPLE_TARGETS -> PlaybackDescriptor.UnsupportedReason.MULTIPLE_TARGETS;
				case INVALID_TARGET -> PlaybackDescriptor.UnsupportedReason.INVALID_TARGET;
			};
			rank = 60;
		} else {
			kind = PlaybackDescriptor.TargetKind.UNSUPPORTED;
			unsupported = PlaybackDescriptor.UnsupportedReason.INVALID_TARGET;
			rank = 60;
		}

		PlaybackDescriptor descriptor = new PlaybackDescriptor(descriptorId, selectionFingerprint,
				request.identity(),
				provider.sourceUuid(), provider.displayName(), stream.name(), stream.title(),
				stream.behaviorHints().videoHash(), stream.behaviorHints().videoSize(),
				stream.behaviorHints().filename(),
				request.metadata(), kind, stream.target(), stream.subtitles(), targetValue, profile,
				endpointValidator, unsupported,
				nowEpochMillis, expiresAt, provider);
		String tieBreaker = normalized(stream.name()) + '\u0000' + normalized(stream.title()) +
				'\u0000' + descriptorId;
		return new Candidate(descriptor, targetFingerprint, rank, tieBreaker);
	}

	private ValidatedPlaybackEndpoint validatePlaybackEndpoint(URI uri,
			me.aap.fermata.addon.stremio.net.NetworkConsent consent,
			me.aap.fermata.addon.stremio.integration.StremioSourceLease sourceLease)
			throws PlaybackRequestValidationException {
		if (!sourceLease.isCurrent()) {
			throw new PlaybackRequestValidationException(
					"Playback provider changed before endpoint validation");
		}
		try {
			var endpoint = NetworkPolicy.validate(uri, consent, addressResolver);
			return new ValidatedPlaybackEndpoint(uri, endpoint.pinnedAddress());
		} catch (IOException error) {
			throw new PlaybackRequestValidationException(
					"Playback endpoint rejected by provider network policy", error);
		}
	}

	private static URI parseHttpUri(String value) {
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			if ((uri.getHost() == null) || (uri.getRawUserInfo() != null) || (scheme == null) ||
					(!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				return null;
			}
			return uri;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private boolean isNetworkAllowed(URI uri,
			me.aap.fermata.addon.stremio.net.NetworkConsent consent, long now) {
		int port = uri.getPort();
		if (port == -1) port = uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
		NetworkDecisionKey key = new NetworkDecisionKey(uri.getScheme().toLowerCase(Locale.ROOT),
				uri.getHost().toLowerCase(Locale.ROOT), port,
				consent.allowCleartext(), consent.allowLan());
		synchronized (networkDecisions) {
			NetworkDecision cached = networkDecisions.get(key);
			if ((cached != null) && (now < cached.expiresAt)) return cached.allowed;
		}
		boolean allowed;
		try {
			NetworkPolicy.validate(uri, consent, addressResolver);
			allowed = true;
		} catch (IOException rejected) {
			allowed = false;
		}
		synchronized (networkDecisions) {
			networkDecisions.put(key,
					new NetworkDecision(allowed, Math.addExact(now, NETWORK_DECISION_TTL_MILLIS)));
		}
		return allowed;
	}

	private static PlaybackDescriptor.TargetKind directKind(URI uri) {
		String path = uri.getPath().toLowerCase(Locale.ROOT);
		if (path.endsWith(".m3u8")) return PlaybackDescriptor.TargetKind.HLS;
		if (path.endsWith(".mpd")) return PlaybackDescriptor.TargetKind.DASH;
		return PlaybackDescriptor.TargetKind.DIRECT_HTTP;
	}

	private static String targetFingerprint(StremioStream stream) {
		String target;
		if (stream.target() instanceof DirectStreamTarget direct) {
			target = "direct\u0000" + direct.url() + "\u0000" +
					headerFingerprint(stream.behaviorHints().proxyHeaders().request());
		} else if (stream.target() instanceof YoutubeStreamTarget youtube) {
			target = "youtube\u0000" + youtube.videoId();
		} else if (stream.target() instanceof ExternalStreamTarget external) {
			target = "external\u0000" + external.url();
		} else if (stream.target() instanceof InfoHashStreamTarget infoHash) {
			target = "infohash\u0000" + infoHash.infoHash().toLowerCase(Locale.ROOT) +
					"\u0000" + infoHash.fileIndex();
		} else if (stream.target() instanceof NzbStreamTarget nzb) {
			target = "nzb\u0000" + nzb.url() + "\u0000" + nzb.fileIndex();
		} else if (stream.target() instanceof ArchiveStreamTarget archive) {
			target = "archive\u0000" + archive.kind() + "\u0000" + archive.sources() +
					"\u0000" + archive.fileIndex();
		} else {
			target = "unsupported\u0000" + stream.target();
		}
		return StremioPlaybackIdentity.digest(target);
	}

	/** Identifies a user-visible stream choice without retaining its expiring target URL. */
	private static String selectionFingerprint(StremioStream stream) {
		String family;
		if (stream.target() instanceof DirectStreamTarget) family = "direct";
		else if (stream.target() instanceof ExternalStreamTarget) family = "external";
		else if (stream.target() instanceof InfoHashStreamTarget) family = "infohash";
		else if (stream.target() instanceof NzbStreamTarget) family = "nzb";
		else if (stream.target() instanceof ArchiveStreamTarget archive) {
			family = "archive-" + archive.kind().name().toLowerCase(Locale.ROOT);
		}
		else if (stream.target() instanceof YoutubeStreamTarget) family = "youtube";
		else family = "unsupported";
		var hints = stream.behaviorHints();
		String semantic = family + '\u0000' + normalized(stream.name()) + '\u0000' +
				normalized(stream.title()) + '\u0000' + normalized(stream.description()) + '\u0000' +
				normalized(hints.bingeGroup());
		return StremioPlaybackIdentity.digest(semantic);
	}

	private static String headerFingerprint(Map<String, String> headers) {
		StringBuilder value = new StringBuilder();
		headers.entrySet().stream().sorted(Comparator
				.comparing((Map.Entry<String, String> entry) ->
						entry.getKey().toLowerCase(Locale.ROOT))
				.thenComparing(Map.Entry::getKey)
				.thenComparing(Map.Entry::getValue))
				.forEach(entry -> value.append(entry.getKey().length()).append(':')
						.append(entry.getKey()).append(entry.getValue().length()).append(':')
						.append(entry.getValue()));
		return StremioPlaybackIdentity.digest(value.toString());
	}

	private static String normalized(String value) {
		return (value == null) ? "" : value.toLowerCase(Locale.ROOT);
	}

	record Candidate(
			PlaybackDescriptor descriptor, String dedupeKey, int rank, String tieBreaker) {
	}

	private record NetworkDecisionKey(String scheme, String host, int port,
			boolean allowCleartext, boolean allowLan) {
	}

	private record NetworkDecision(boolean allowed, long expiresAt) {
	}
}
