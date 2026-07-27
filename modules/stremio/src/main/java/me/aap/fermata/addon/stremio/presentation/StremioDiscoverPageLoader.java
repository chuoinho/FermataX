package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;

/** Owns Discover route loading, filters and bounded accumulated page state. */
final class StremioDiscoverPageLoader implements AutoCloseable {
	private static final int MAX_DISCOVER_SESSIONS = 8;
	private static final int MAX_DISCOVER_ITEMS = 500;
	private static final Comparator<CatalogDescriptor> CATALOG_ORDER =
			Comparator.comparingInt(CatalogDescriptor::providerPosition)
					.thenComparingInt(CatalogDescriptor::catalogPosition);

	private final StremioItemGateway items;
	private final StremioPresentationText text;
	private final StremioPresentationFormatter formatter;
	private final Function<BrowseMedia, String> rememberMedia;
	private final Map<DiscoverKey, DiscoverAccumulator> pages =
			new LinkedHashMap<>(8, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<DiscoverKey, DiscoverAccumulator> eldest) {
					return size() > MAX_DISCOVER_SESSIONS;
				}
			};

	StremioDiscoverPageLoader(StremioItemGateway items, StremioPresentationText text,
			StremioPresentationFormatter formatter,
			Function<BrowseMedia, String> rememberMedia) {
		this.items = Objects.requireNonNull(items, "items");
		this.text = Objects.requireNonNull(text, "text");
		this.formatter = Objects.requireNonNull(formatter, "formatter");
		this.rememberMedia = Objects.requireNonNull(rememberMedia, "rememberMedia");
	}

	CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, StremioRoute.Discover route) {
		return request.track(items.catalogs()).thenCompose(catalogs -> {
			request.ensureActive();
			List<CatalogDescriptor> visible = catalogs.stream()
					.filter(StremioDiscoverPageLoader::isSupportedCatalog)
					.sorted(CATALOG_ORDER).toList();
			CatalogDescriptor selected = visible.stream()
					.filter(catalog -> catalogKey(catalog).equals(route.catalogKey()))
					.findFirst().orElseThrow(() ->
							new IllegalStateException("Catalog is unavailable"));
			String genre = route.genre().isEmpty() ? initialGenre(selected) : route.genre();
			Map<String, List<String>> extras = effectiveExtras(selected, route.extras());
			StremioRoute.Discover effectiveRoute = new StremioRoute.Discover(
					route.catalogKey(), genre, route.skip(), extras);
			if (!missingRequiredExtras(selected, effectiveRoute).isEmpty()) {
				return CompletableFuture.completedFuture(inputRequiredPage(
						request, effectiveRoute, visible, selected));
			}
			return request.track(items.catalog(selected.route(), emptyToNull(genre),
					route.skip(), extras)).thenApply(page -> page(
					request, effectiveRoute, visible, selected, page));
		});
	}

	private StremioPresentationPage inputRequiredPage(StremioPresentationRequest request,
			StremioRoute.Discover route, List<CatalogDescriptor> catalogs,
			CatalogDescriptor selected) {
		request.ensureActive();
		StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
		addFilters(builder, catalogs, selected, route);
		builder.models.add(StremioPresentationModels.state(
				"state:catalog-input:" + route.catalogKey(),
				text.label(StremioPresentationText.Label.CATALOG_REQUIRES_INPUT),
				StremioUiModel.StateKind.WARNING));
		return builder.build();
	}

	private StremioPresentationPage page(StremioPresentationRequest request,
			StremioRoute.Discover route, List<CatalogDescriptor> catalogs,
			CatalogDescriptor selected, CatalogPage page) {
		request.ensureActive();
		StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
		addFilters(builder, catalogs, selected, route);

		DiscoverMerge accumulated = accumulate(route, page);
		for (BrowseMedia media : accumulated.items()) {
			String detailsKey = rememberMedia.apply(media);
			String key = "discover:" + route.catalogKey() + ':' + detailsKey;
			builder.add(StremioPresentationModels.poster(key, media, 0f),
					new StremioSelection.Navigate(new StremioRoute.Details(detailsKey)));
		}
		if (accumulated.hasNext() && (page.nextSkip() > route.skip())) {
			String key = "action:next:" + route.catalogKey() + ':' + page.nextSkip();
			builder.add(new StremioUiModel.Action(key,
					text.action(StremioUiModel.ActionKind.NEXT_PAGE),
					StremioUiModel.ActionKind.NEXT_PAGE),
					new StremioSelection.Navigate(new StremioRoute.Discover(
							route.catalogKey(), route.genre(), page.nextSkip(), route.extras()), true));
		}
		if (builder.models.stream().noneMatch(StremioUiModel.Poster.class::isInstance)) {
			builder.models.add(StremioPresentationModels.state("state:no-content",
					text.label(StremioPresentationText.Label.NO_CONTENT),
					StremioUiModel.StateKind.EMPTY));
		}
		return builder.build();
	}

	private DiscoverMerge accumulate(StremioRoute.Discover route, CatalogPage page) {
		DiscoverKey key = new DiscoverKey(route.catalogKey(),
				Objects.requireNonNullElse(route.genre(), ""), route.extras());
		synchronized (pages) {
			DiscoverAccumulator accumulator = pages.computeIfAbsent(
					key, ignored -> new DiscoverAccumulator());
			if (route.skip() == 0) accumulator.pages.clear();
			boolean replacingPage = accumulator.pages.containsKey(route.skip());
			java.util.HashSet<String> priorIds = new java.util.HashSet<>();
			for (var entry : accumulator.pages.entrySet()) {
				if (entry.getKey() == route.skip()) continue;
				for (BrowseMedia media : entry.getValue()) priorIds.add(media.scopedId());
			}
			int addedItems = 0;
			for (BrowseMedia media : page.items()) {
				if (priorIds.add(media.scopedId())) addedItems++;
			}
			accumulator.pages.put(route.skip(), List.copyOf(page.items()));
			LinkedHashMap<String, BrowseMedia> merged = new LinkedHashMap<>();
			for (List<BrowseMedia> values : accumulator.pages.values()) {
				for (BrowseMedia media : values) {
					merged.putIfAbsent(media.scopedId(), media);
					if (merged.size() == MAX_DISCOVER_ITEMS) {
						return new DiscoverMerge(List.copyOf(merged.values()), false);
					}
				}
			}
			boolean madeProgress = (route.skip() == 0) || replacingPage || (addedItems > 0);
			return new DiscoverMerge(List.copyOf(merged.values()),
					page.hasNext() && madeProgress);
		}
	}

	private void addFilters(StremioPresentationPageBuilder builder,
			List<CatalogDescriptor> catalogs, CatalogDescriptor selected,
			StremioRoute.Discover route) {
		addTypeFilter(builder, catalogs, selected);
		addCatalogFilter(builder, catalogs, selected);
		addGenreFilter(builder, selected, route);
		addExtraFilters(builder, selected, route);
	}

	private void addTypeFilter(StremioPresentationPageBuilder builder,
			List<CatalogDescriptor> catalogs, CatalogDescriptor selected) {
		Map<String, CatalogDescriptor> types = new LinkedHashMap<>();
		for (CatalogDescriptor catalog : catalogs) {
			types.putIfAbsent(catalog.route().type(), catalog);
		}
		List<StremioUiModel.Option> options = new ArrayList<>();
		String selectedKey = "filter:type:" + selected.route().type();
		for (var entry : types.entrySet()) {
			String key = "filter:type:" + entry.getKey();
			options.add(new StremioUiModel.Option(key, formatter.typeLabel(entry.getKey())));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(entry.getValue()),
							initialGenre(entry.getValue()), 0), true));
		}
		builder.models.add(new StremioUiModel.Filter("filter:type",
				text.label(StremioPresentationText.Label.TYPE), selectedKey, options));
	}

	private void addCatalogFilter(StremioPresentationPageBuilder builder,
			List<CatalogDescriptor> catalogs, CatalogDescriptor selected) {
		List<StremioUiModel.Option> options = new ArrayList<>();
		for (CatalogDescriptor catalog : catalogs) {
			if (!catalog.route().type().equals(selected.route().type())) continue;
			String key = "filter:catalog:" + catalogKey(catalog);
			options.add(new StremioUiModel.Option(key,
					StremioPresentationFormatter.catalogFilterLabel(catalog, catalogs)));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(catalog), initialGenre(catalog), 0), true));
		}
		builder.models.add(new StremioUiModel.Filter("filter:catalog",
				text.label(StremioPresentationText.Label.CATALOG),
				"filter:catalog:" + catalogKey(selected), options));
	}

	private void addGenreFilter(StremioPresentationPageBuilder builder,
			CatalogDescriptor selected, StremioRoute.Discover route) {
		List<StremioUiModel.Option> options = new ArrayList<>();
		String genre = route.genre();
		boolean required = !initialGenre(selected).isEmpty() || selected.extras().stream()
				.anyMatch(extra -> extra.required() && extra.name().equalsIgnoreCase("genre"));
		String selectedKey = "";
		if (!required) {
			String allKey = "filter:genre:" + catalogKey(selected) + ":all";
			options.add(new StremioUiModel.Option(allKey,
					text.label(StremioPresentationText.Label.ALL_GENRES)));
			builder.selections.put(allKey, new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(selected), "", 0,
							route.extras()), true));
			selectedKey = allKey;
		}
		for (String value : selected.genres()) {
			String key = "filter:genre:" + catalogKey(selected) + ':' + options.size();
			options.add(new StremioUiModel.Option(key, value));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(selected), value, 0,
							route.extras()), true));
			if (value.equals(genre)) selectedKey = key;
		}
		if (selectedKey.isEmpty() && !options.isEmpty()) selectedKey = options.get(0).stableKey();
		builder.models.add(new StremioUiModel.Filter("filter:genre",
				text.label(StremioPresentationText.Label.GENRE), selectedKey, options));
	}

	private void addExtraFilters(StremioPresentationPageBuilder builder,
			CatalogDescriptor selected, StremioRoute.Discover route) {
		for (var extra : selected.extras()) {
			if (extra.name().equalsIgnoreCase("genre") || extra.name().equalsIgnoreCase("skip") ||
					extra.options().isEmpty()) continue;
			List<StremioUiModel.Option> options = new ArrayList<>();
			String selectedValue = first(route.extras().get(extra.name()));
			String selectedKey = "";
			if (!extra.required()) {
				String allKey = extraFilterKey(selected, extra.name(), "all");
				options.add(new StremioUiModel.Option(allKey,
						text.label(StremioPresentationText.Label.ALL)));
				builder.selections.put(allKey, new StremioSelection.Navigate(
						withExtra(route, extra.name(), List.of()), true));
			}
			for (int i = 0; i < extra.options().size(); i++) {
				String value = extra.options().get(i);
				String key = extraFilterKey(selected, extra.name(), Integer.toString(i));
				options.add(new StremioUiModel.Option(key, value));
				builder.selections.put(key, new StremioSelection.Navigate(
						withExtra(route, extra.name(), List.of(value)), true));
				if (value.equals(selectedValue)) selectedKey = key;
			}
			if (selectedKey.isEmpty() && !extra.required() && !options.isEmpty()) {
				selectedKey = options.get(0).stableKey();
			}
			if (!options.isEmpty()) builder.models.add(new StremioUiModel.Filter(
					"filter:extra:" + catalogKey(selected) + ':' + extra.name(),
					text.extra(extra.name()), selectedKey, options));
		}
	}

	private static String initialGenre(CatalogDescriptor catalog) {
		for (var extra : catalog.extras()) {
			if (extra.required() && extra.name().equalsIgnoreCase("genre")) {
				return defaultRequiredGenre(catalog, extra.options());
			}
		}
		return "";
	}

	private static String defaultRequiredGenre(
			CatalogDescriptor catalog, List<String> options) {
		List<String> values = catalog.genres().isEmpty() ? options : catalog.genres();
		for (String value : values) {
			if ((value != null) && !value.isBlank()) return value.trim();
		}
		return "";
	}

	private static Map<String, List<String>> effectiveExtras(
			CatalogDescriptor catalog, Map<String, List<String>> requested) {
		Map<String, List<String>> effective = new LinkedHashMap<>();
		for (var extra : catalog.extras()) {
			if (extra.name().equalsIgnoreCase("genre") || extra.name().equalsIgnoreCase("skip")) {
				continue;
			}
			List<String> values = requested.get(extra.name());
			if ((values != null) && !values.isEmpty()) effective.put(extra.name(), values);
			else if (extra.required() && !extra.options().isEmpty()) {
				effective.put(extra.name(), List.of(extra.options().get(0)));
			}
		}
		return effective.isEmpty() ? Map.of() : Map.copyOf(effective);
	}

	private static List<String> missingRequiredExtras(
			CatalogDescriptor catalog, StremioRoute.Discover route) {
		List<String> missing = new ArrayList<>();
		if (catalog.extras().stream().anyMatch(extra -> extra.required() &&
				extra.name().equalsIgnoreCase("genre")) && route.genre().isEmpty()) {
			missing.add("genre");
		}
		for (var extra : catalog.extras()) {
			if (!extra.required() || extra.name().equalsIgnoreCase("genre") ||
					extra.name().equalsIgnoreCase("skip")) continue;
			List<String> values = route.extras().get(extra.name());
			if ((values == null) || values.isEmpty()) missing.add(extra.name());
		}
		return List.copyOf(missing);
	}

	private static StremioRoute.Discover withExtra(StremioRoute.Discover route,
			String name, List<String> values) {
		Map<String, List<String>> extras = new LinkedHashMap<>(route.extras());
		if (values.isEmpty()) extras.remove(name);
		else extras.put(name, List.copyOf(values));
		return new StremioRoute.Discover(route.catalogKey(), route.genre(), 0, extras);
	}

	private static String extraFilterKey(CatalogDescriptor catalog, String name, String value) {
		return "filter:extra:" + catalogKey(catalog) + ':' + name + ':' + value;
	}

	private static String first(List<String> values) {
		return ((values == null) || values.isEmpty()) ? "" : values.get(0);
	}

	private static String catalogKey(CatalogDescriptor catalog) {
		return StremioItemIds.catalog(catalog);
	}

	private static boolean isSupportedCatalog(CatalogDescriptor catalog) {
		return !catalog.route().type().equalsIgnoreCase("youtube");
	}

	private static String emptyToNull(String value) {
		return ((value == null) || value.isBlank()) ? null : value;
	}

	@Override
	public void close() {
		synchronized (pages) {
			pages.clear();
		}
	}

	private record DiscoverKey(String catalogKey, String genre,
			Map<String, List<String>> extras) {
	}

	private record DiscoverMerge(List<BrowseMedia> items, boolean hasNext) {
	}

	private static final class DiscoverAccumulator {
		private final java.util.SortedMap<Integer, List<BrowseMedia>> pages =
				new java.util.TreeMap<>();
	}
}
