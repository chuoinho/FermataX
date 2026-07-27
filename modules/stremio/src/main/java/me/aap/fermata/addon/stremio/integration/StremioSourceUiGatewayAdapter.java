package me.aap.fermata.addon.stremio.integration;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.model.source.TransportFingerprint;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.ManifestValidator;
import me.aap.fermata.addon.stremio.protocol.model.AddonCatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.protocol.response.StremioAddonCatalogEntry;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeSources;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeHttpClient;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.addon.stremio.source.StremioSourceInput;
import me.aap.fermata.addon.stremio.source.StremioSourceOutcome;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.addon.stremio.ui.source.SourceUiCapability;
import me.aap.fermata.addon.stremio.ui.source.SourceUiConsent;
import me.aap.fermata.addon.stremio.ui.source.SourceUiDraft;
import me.aap.fermata.addon.stremio.ui.source.SourceUiDiscoveryItem;
import me.aap.fermata.addon.stremio.ui.source.SourceUiError;
import me.aap.fermata.addon.stremio.ui.source.SourceUiFailure;
import me.aap.fermata.addon.stremio.ui.source.SourceUiGateway;
import me.aap.fermata.addon.stremio.ui.source.SourceUiItem;
import me.aap.fermata.addon.stremio.ui.source.SourceUiOperation;
import me.aap.fermata.addon.stremio.ui.source.SourceUiResult;
import me.aap.fermata.addon.stremio.ui.source.SourceUiSnapshot;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigLaunch;
import me.aap.fermata.addon.stremio.ui.config.StremioConfigResult;

/** Secret-safe production adapter between source UI state and the runtime facade. */
public final class StremioSourceUiGatewayAdapter implements SourceUiGateway, AutoCloseable {
	private static final int MAX_DISCOVERED_ADDONS = 100;
	private final SourceAccess sources;
	private final StremioProviderSecretAccess secrets;
	private final StremioRuntimeHttpClient configHttp;
	private final StremioProtocolClient protocolClient;
	private final StremioProviderCatalog providers;
	private final RequestGeneration discoveryGeneration = new RequestGeneration();
	private final Map<String, DiscoveredAddon> discovered = new LinkedHashMap<>();
	private static final long MAX_CONFIG_RESOURCE_BYTES = 2L * 1024L * 1024L;

	public StremioSourceUiGatewayAdapter(StremioRuntimeSources sources,
			StremioProviderSecretAccess secrets, StremioRuntimeHttpClient configHttp) {
		this(sources, secrets, configHttp, null, null);
	}

	public StremioSourceUiGatewayAdapter(StremioRuntimeSources sources,
			StremioProviderSecretAccess secrets, StremioRuntimeHttpClient configHttp,
			StremioProtocolClient protocolClient, StremioProviderCatalog providers) {
		Objects.requireNonNull(sources, "sources");
		this.sources = new SourceAccess() {
			@Override
			public CompletableFuture<StremioSourceSnapshot> sources() {
				return sources.sources();
			}

			@Override
			public AutoCloseable observe(Consumer<StremioSourceSnapshot> observer) {
				return sources.observe(observer);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> add(StremioSourceInput input) {
				return sources.add(input);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> edit(
					String sourceUuid, StremioSourceInput input) {
				return sources.edit(sourceUuid, input);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> enable(String sourceUuid) {
				return sources.enable(sourceUuid);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> disable(String sourceUuid) {
				return sources.disable(sourceUuid);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> refresh(String sourceUuid) {
				return sources.refresh(sourceUuid);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> remove(String sourceUuid) {
				return sources.remove(sourceUuid);
			}

			@Override
			public CompletableFuture<StremioSourceOutcome> reorder(List<String> ids) {
				return sources.reorder(ids);
			}
		};
		this.secrets = Objects.requireNonNull(secrets, "secrets");
		this.configHttp = Objects.requireNonNull(configHttp, "configHttp");
		this.protocolClient = protocolClient;
		this.providers = providers;
	}

	StremioSourceUiGatewayAdapter(SourceAccess sources,
			StremioProviderSecretAccess secrets) {
		this.sources = Objects.requireNonNull(sources, "sources");
		this.secrets = Objects.requireNonNull(secrets, "secrets");
		configHttp = null;
		protocolClient = null;
		providers = null;
	}

	@Override
	public CompletableFuture<SourceUiSnapshot> load() {
		return sources.sources().thenApply(this::snapshot);
	}

	@Override
	public CompletableFuture<SourceUiDraft> loadDraft(String sourceUuid) {
		return sources.sources().thenCompose(snapshot -> {
			StremioSourceRecord source = snapshot.source(sourceUuid);
			if (source == null) return CompletableFuture.failedFuture(
					new SourceUiFailure(SourceUiError.NOT_FOUND));
			return secrets.load(source).thenApply(secret -> {
				if (secret == null) throw new SourceUiFailure(SourceUiError.SECURE_STORAGE);
				return new SourceUiDraft(secret.transportUrl(),
						secret.configurationToken() == null ? "" : secret.configurationToken(),
						consent(source));
			}).toCompletableFuture();
		});
	}

	@Override
	public CompletableFuture<StremioConfigLaunch> loadConfiguration(String sourceUuid) {
		return sources.sources().thenCompose(snapshot -> {
			StremioSourceRecord source = snapshot.source(sourceUuid);
			if ((source == null) || !configurable(source)) return CompletableFuture.failedFuture(
					new SourceUiFailure(SourceUiError.NOT_FOUND));
			return secrets.load(source).thenApply(secret -> {
				if (secret == null) throw new SourceUiFailure(SourceUiError.SECURE_STORAGE);
				var consent = new me.aap.fermata.addon.stremio.net.NetworkConsent(
						source.allowCleartext(), source.allowLan());
				return new StremioConfigLaunch(secret, consent, (uri, headers) -> {
					if (configHttp == null) return java.util.concurrent.CompletableFuture
							.failedFuture(new IllegalStateException(
									"Configuration transport is unavailable"));
					return configHttp.fetchRaw(uri, headers, MAX_CONFIG_RESOURCE_BYTES, consent)
							.response().thenApply(response ->
									new me.aap.fermata.addon.stremio.ui.config
											.StremioConfigResourceLoader.Response(
											response.status(), response.finalUri(),
											response.headers(), response.body()));
				});
			}).toCompletableFuture();
		});
	}

	@Override
	public AutoCloseable observe(Consumer<SourceUiSnapshot> observer) {
		Objects.requireNonNull(observer, "observer");
		return sources.observe(snapshot -> observer.accept(snapshot(snapshot)));
	}

	@Override
	public CompletableFuture<List<SourceUiDiscoveryItem>> discover() {
		if ((protocolClient == null) || (providers == null)) {
			return CompletableFuture.completedFuture(List.of());
		}
		RequestGeneration.Token generation = discoveryGeneration.begin();
		CompletableFuture<List<SourceUiDiscoveryItem>> result = providers.browseProviders()
				.thenCompose(providerList -> {
			List<CompletableFuture<List<StremioAddonCatalogEntry>>> calls = new ArrayList<>();
			for (BrowseProvider provider : providerList) {
				for (AddonCatalogCapability catalog : provider.manifest().addonCatalogs()) {
					calls.add(loadAddonCatalog(provider, catalog, generation));
				}
			}
			CompletableFuture<Void> all = CompletableFuture.allOf(
					calls.toArray(CompletableFuture[]::new));
			return all.thenCompose(ignored -> sources.sources().thenApply(snapshot ->
					projectDiscovered(calls, snapshot, generation)));
		}).toCompletableFuture();
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) discoveryGeneration.cancelAll();
		});
		return result;
	}

	@Override
	public SourceUiOperation installDiscovered(String stableId) {
		DiscoveredAddon discoveredAddon;
		synchronized (discovered) {
			discoveredAddon = discovered.get(stableId);
		}
		if (discoveredAddon == null) return SourceUiGateway.super.installDiscovered(stableId);
		CompletableFuture<StremioSourceOutcome> addition = sources.add(new StremioSourceInput(
				discoveredAddon.entry().transportUrl(), null,
				me.aap.fermata.addon.stremio.net.NetworkConsent.STRICT));
		CompletableFuture<SourceUiResult> verified = addition.thenCompose(outcome -> {
			StremioSourceRecord source = outcome.source();
			if ((source == null) || source.addonId().equals(discoveredAddon.expectedAddonId())) {
				return CompletableFuture.completedFuture(result(outcome));
			}
			return sources.remove(source.sourceUuid()).handle((rollback, error) ->
					SourceUiResult.failed((error == null) && (rollback != null) &&
							rollback.changed() ? SourceUiError.INVALID_MANIFEST :
							SourceUiError.ROLLBACK));
		});
		return SourceUiOperation.of(verified, () -> addition.cancel(true));
	}

	private CompletableFuture<List<StremioAddonCatalogEntry>> loadAddonCatalog(
			BrowseProvider provider, AddonCatalogCapability catalog,
			RequestGeneration.Token generation) {
		StremioProtocolClient.ProtocolCall call = protocolClient.fetch(provider.sourceUuid(),
				provider.manifest().id(), new StremioRequest("addon_catalog",
				catalog.type(), catalog.id()), generation);
		return call.response().handle((payload, error) -> {
			if ((error != null) || !generation.isCurrent()) return List.of();
			try {
				return StremioResponseParser.parseAddonCatalog(payload.body()).addons();
			} catch (RuntimeException invalid) {
				return List.of();
			}
		});
	}

	private List<SourceUiDiscoveryItem> projectDiscovered(
			List<CompletableFuture<List<StremioAddonCatalogEntry>>> calls,
			StremioSourceSnapshot snapshot, RequestGeneration.Token generation) {
		if (!generation.isCurrent()) return List.of();
		Map<String, DiscoveredAddon> unique = new LinkedHashMap<>();
		for (CompletableFuture<List<StremioAddonCatalogEntry>> call : calls) {
			for (StremioAddonCatalogEntry entry : call.join()) {
				if (youtubeOnly(entry)) continue;
				String stableId;
				try {
					stableId = entry.manifest().id() + ':' +
							TransportFingerprint.create(entry.transportUrl());
				} catch (RuntimeException invalid) {
					continue;
				}
				unique.putIfAbsent(stableId,
						new DiscoveredAddon(entry, entry.manifest().id()));
				if (unique.size() == MAX_DISCOVERED_ADDONS) break;
			}
			if (unique.size() == MAX_DISCOVERED_ADDONS) break;
		}
		Set<String> installedIds = snapshot.sources().stream()
				.map(StremioSourceRecord::addonId).collect(java.util.stream.Collectors.toSet());
		List<SourceUiDiscoveryItem> result = new ArrayList<>(unique.size());
		for (Map.Entry<String, DiscoveredAddon> value : unique.entrySet()) {
			StremioAddonCatalogEntry entry = value.getValue().entry();
			var manifest = entry.manifest();
			result.add(new SourceUiDiscoveryItem(value.getKey(), manifest.name(),
					manifest.description(), manifest.version(), false,
					entry.protectedAddon(), manifest.behaviorHints().configurable(),
					installedIds.contains(manifest.id())));
		}
		synchronized (discovered) {
			discovered.clear();
			discovered.putAll(unique);
		}
		return List.copyOf(result);
	}

	private static boolean youtubeOnly(StremioAddonCatalogEntry entry) {
		String identity = (entry.manifest().id() + ' ' + entry.manifest().name() + ' ' +
				entry.transportName()).toLowerCase(Locale.ROOT);
		return identity.contains("youtube");
	}

	private record DiscoveredAddon(
			StremioAddonCatalogEntry entry, String expectedAddonId) {
		private DiscoveredAddon {
			Objects.requireNonNull(entry, "entry");
			Objects.requireNonNull(expectedAddonId, "expectedAddonId");
		}
	}

	@Override
	public SourceUiOperation add(SourceUiDraft draft) {
		return operation(sources.add(input(draft)), null);
	}

	@Override
	public SourceUiOperation edit(String sourceUuid, SourceUiDraft draft) {
		return operation(sources.edit(sourceUuid, input(draft)), sourceUuid);
	}

	@Override
	public SourceUiOperation configure(String sourceUuid, StremioConfigResult result) {
		Objects.requireNonNull(result, "result");
		AtomicReference<String> configuredUrl = new AtomicReference<>();
		result.consumeUrl(configuredUrl::set);
		CompletableFuture<StremioSourceOutcome> configured = sources.sources().thenCompose(snapshot -> {
			StremioSourceRecord source = snapshot.source(sourceUuid);
			if (source == null) return CompletableFuture.failedFuture(
					new SourceUiFailure(SourceUiError.NOT_FOUND));
			return sources.edit(sourceUuid, new StremioSourceInput(configuredUrl.get(), null,
					new me.aap.fermata.addon.stremio.net.NetworkConsent(
							source.allowCleartext(), source.allowLan())));
		});
		return operation(configured, sourceUuid);
	}

	@Override
	public SourceUiOperation setEnabled(String sourceUuid, boolean enabled) {
		return operation(enabled ? sources.enable(sourceUuid) : sources.disable(sourceUuid), sourceUuid);
	}

	@Override
	public SourceUiOperation refresh(String sourceUuid) {
		return operation(sources.refresh(sourceUuid), sourceUuid);
	}

	@Override
	public SourceUiOperation remove(String sourceUuid) {
		return operation(sources.remove(sourceUuid), sourceUuid);
	}

	@Override
	public SourceUiOperation reorder(List<String> orderedSourceUuids) {
		return operation(sources.reorder(orderedSourceUuids), null);
	}

	private SourceUiOperation operation(CompletableFuture<StremioSourceOutcome> source,
			String requestedSourceUuid) {
		CompletableFuture<SourceUiResult> completion = source.thenApply(outcome -> {
			return result(outcome);
		});
		return SourceUiOperation.of(completion, () -> source.cancel(true));
	}

	private SourceUiResult result(StremioSourceOutcome outcome) {
		return switch (outcome.status()) {
			case CHANGED -> SourceUiResult.changed(snapshot(outcome.snapshot()));
			case UNCHANGED -> SourceUiResult.unchanged(snapshot(outcome.snapshot()));
			case CANCELLED -> SourceUiResult.cancelled();
			case FAILED -> SourceUiResult.failed(error(outcome.errorCode()));
		};
	}

	private SourceUiSnapshot snapshot(StremioSourceSnapshot snapshot) {
		List<SourceUiItem> items = snapshot.sources().stream().map(source ->
				new SourceUiItem(source.sourceUuid(), source.name(), source.version(),
						source.redactedTransportUrl(), source.enabled(), source.position(),
						source.lastErrorCode(), configurable(source), consent(source),
						capabilities(source))).toList();
		return new SourceUiSnapshot(snapshot.revision(), items);
	}

	private static Set<SourceUiCapability> capabilities(StremioSourceRecord source) {
		try {
			var manifest = ManifestValidator.parse(source.manifestJson());
			var capabilities = EnumSet.noneOf(SourceUiCapability.class);
			if (!manifest.catalogs().isEmpty()) capabilities.add(SourceUiCapability.CATALOG);
			if (!manifest.addonCatalogs().isEmpty()) {
				capabilities.add(SourceUiCapability.ADDON_CATALOG);
			}
			for (var resource : manifest.resources()) {
				switch (resource.name().toLowerCase(Locale.ROOT)) {
					case "catalog" -> capabilities.add(SourceUiCapability.CATALOG);
					case "meta" -> capabilities.add(SourceUiCapability.META);
					case "stream" -> capabilities.add(SourceUiCapability.STREAM);
					case "subtitle", "subtitles" ->
							capabilities.add(SourceUiCapability.SUBTITLE);
					case "addon_catalog" -> capabilities.add(SourceUiCapability.ADDON_CATALOG);
					default -> {
					}
				}
			}
			return Set.copyOf(capabilities);
		} catch (RuntimeException ignored) {
			// A stale invalid manifest must not expose raw data or break source management.
			return Set.of();
		}
	}

	private static StremioSourceInput input(SourceUiDraft draft) {
		Objects.requireNonNull(draft, "draft");
		return new StremioSourceInput(draft.transportUrl(), draft.configurationToken(),
				new me.aap.fermata.addon.stremio.net.NetworkConsent(
						draft.consent().allowCleartext(), draft.consent().allowLan()));
	}

	private static SourceUiConsent consent(StremioSourceRecord source) {
		return new SourceUiConsent(source.allowCleartext(), source.allowLan());
	}

	private static boolean configurable(StremioSourceRecord source) {
		try {
			var hints = new org.json.JSONObject(source.manifestJson())
					.optJSONObject("behaviorHints");
			if ((hints != null) && (hints.optBoolean("configurable", false) ||
					hints.optBoolean("configurationRequired", false))) return true;
		} catch (Exception error) {
		}
		// Stored manifests have already passed ManifestValidator. This fallback keeps UI capability
		// detection deterministic on Android JSON implementations with incomplete optBoolean support.
		return source.manifestJson().matches("(?s).*\\\"(?:configurable|configurationRequired)\\\"" +
				"\\s*:\\s*true.*");
	}

	private static SourceUiError error(StremioSourceException.Code code) {
		if (code == null) return SourceUiError.UNKNOWN;
		return switch (code) {
			case CANCELLED -> SourceUiError.CANCELLED;
			case CLOSED -> SourceUiError.CLOSED;
			case CONCURRENT_MODIFICATION -> SourceUiError.CONCURRENT_MODIFICATION;
			case DUPLICATE_TRANSPORT -> SourceUiError.DUPLICATE_SOURCE;
			case INVALID_MANIFEST -> SourceUiError.INVALID_MANIFEST;
			case INVALID_ORDER -> SourceUiError.INVALID_ORDER;
			case INVALID_TRANSPORT -> SourceUiError.INVALID_URL;
			case NOT_FOUND -> SourceUiError.NOT_FOUND;
			case PERSISTENCE -> SourceUiError.PERSISTENCE;
			case ROLLBACK -> SourceUiError.ROLLBACK;
			case SECRET_TAINT -> SourceUiError.SECRET_REJECTED;
			case SECURE_STORAGE -> SourceUiError.SECURE_STORAGE;
			case TRANSPORT -> SourceUiError.TRANSPORT;
		};
	}

	@Override
	public void close() {
		discoveryGeneration.close();
		synchronized (discovered) {
			discovered.clear();
		}
	}

	interface SourceAccess {
		CompletableFuture<StremioSourceSnapshot> sources();

		AutoCloseable observe(Consumer<StremioSourceSnapshot> observer);

		CompletableFuture<StremioSourceOutcome> add(StremioSourceInput input);

		CompletableFuture<StremioSourceOutcome> edit(String sourceUuid, StremioSourceInput input);

		CompletableFuture<StremioSourceOutcome> enable(String sourceUuid);

		CompletableFuture<StremioSourceOutcome> disable(String sourceUuid);

		CompletableFuture<StremioSourceOutcome> refresh(String sourceUuid);

		CompletableFuture<StremioSourceOutcome> remove(String sourceUuid);

		CompletableFuture<StremioSourceOutcome> reorder(List<String> ids);
	}
}
