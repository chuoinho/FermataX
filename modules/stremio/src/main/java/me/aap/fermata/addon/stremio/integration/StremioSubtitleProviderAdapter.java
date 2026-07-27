package me.aap.fermata.addon.stremio.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;
import me.aap.fermata.addon.stremio.subtitle.SubtitleCandidate;
import me.aap.fermata.addon.stremio.subtitle.SubtitleProvider;
import me.aap.fermata.addon.stremio.subtitle.SubtitleRequestContext;

/** One enabled provider/resource binding for the subtitle coordinator. */
public final class StremioSubtitleProviderAdapter implements SubtitleProvider {
	private static final Duration DESCRIPTOR_TTL = Duration.ofMinutes(15);

	private final StremioProtocolClient client;
	private final String sourceUuid;
	private final String addonId;
	private final String providerLabel;
	private final String type;
	private final SubtitleRequestContext context;
	private final String requestId;
	private final Clock clock;

	public StremioSubtitleProviderAdapter(StremioProtocolClient client,
			String sourceUuid, String addonId, String providerLabel,
			String type, String videoId) {
		this(client, sourceUuid, addonId, providerLabel, type,
				SubtitleRequestContext.forVideo(videoId), Clock.systemUTC());
	}

	public StremioSubtitleProviderAdapter(StremioProtocolClient client,
			String sourceUuid, String addonId, String providerLabel,
			String type, SubtitleRequestContext context) {
		this(client, sourceUuid, addonId, providerLabel, type, context, Clock.systemUTC());
	}

	StremioSubtitleProviderAdapter(StremioProtocolClient client,
			String sourceUuid, String addonId, String providerLabel,
			String type, String videoId, Clock clock) {
		this(client, sourceUuid, addonId, providerLabel, type,
				SubtitleRequestContext.forVideo(videoId), clock);
	}

	StremioSubtitleProviderAdapter(StremioProtocolClient client,
			String sourceUuid, String addonId, String providerLabel,
			String type, SubtitleRequestContext context, Clock clock) {
		this(client, sourceUuid, addonId, providerLabel, type, context,
				(context.videoHash() == null) ? context.videoId() : context.videoHash(), clock);
	}

	StremioSubtitleProviderAdapter(StremioProtocolClient client,
			String sourceUuid, String addonId, String providerLabel,
			String type, SubtitleRequestContext context, String requestId, Clock clock) {
		this.client = Objects.requireNonNull(client, "client");
		this.sourceUuid = requireText(sourceUuid, "sourceUuid");
		this.addonId = requireText(addonId, "addonId");
		this.providerLabel = requireText(providerLabel, "providerLabel");
		this.type = requireText(type, "type");
		this.context = Objects.requireNonNull(context, "context");
		this.requestId = requireText(requestId, "requestId");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public String providerKey() {
		return sourceUuid;
	}

	@Override
	public SubtitleProviderCall load(RequestGeneration.Token generation) {
		Map<String, Object> extras = new java.util.LinkedHashMap<>();
		extras.put("videoId", context.videoId());
		if (context.videoSize() != null) extras.put("videoSize", context.videoSize());
		if (context.filename() != null) extras.put("filename", context.filename());
		StremioRequest request = new StremioRequest("subtitles", type, requestId, extras);
		StremioProtocolClient.ProtocolCall call = client.fetch(
				sourceUuid, addonId, request, generation);
		CompletableFuture<List<SubtitleCandidate>> response = call.response().thenApply(payload -> {
			Instant expiresAt = clock.instant().plus(DESCRIPTOR_TTL);
			return StremioResponseParser.parseSubtitles(payload.body()).subtitles().stream()
					.map(subtitle -> new SubtitleCandidate(subtitle.id(), subtitle.url(),
							subtitle.language(), sourceUuid, providerLabel,
							SubtitleCandidate.Source.PROVIDER, null, -1, null, expiresAt))
					.map(candidate -> new SubtitleCandidate(candidate.subtitleId(), candidate.url(),
						candidate.languageLabel(), candidate.providerKey(), candidate.providerLabel(),
						candidate.source(), candidate.formatHint(), candidate.declaredSizeBytes(),
						candidate.requestHeaders(), payload.sourceLease(), candidate.expiresAt()))
					.toList();
		}).handle((value, error) -> {
			if (error != null) throw StremioIntegrationException.redactResponseFailure(error);
			return value;
		});
		return new SubtitleProviderCall() {
			@Override
			public CompletableFuture<List<SubtitleCandidate>> response() {
				return response;
			}

			@Override
			public void cancel() {
				call.cancel();
			}
		};
	}

	@Override
	public String toString() {
		return "StremioSubtitleProviderAdapter[provider=<redacted>]";
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
