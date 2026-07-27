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
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;

/** Builds the incrementally published Home page and owns its shelf policy. */
final class StremioHomePageLoader {
	private static final int MAX_HOME_SECTIONS = 6;
	private static final int MAX_SECTION_POSTERS = 12;
	private static final int MAX_HOME_PROVIDERS_PER_SHELF = 4;
	private static final Comparator<CatalogDescriptor> CATALOG_ORDER =
			Comparator.comparingInt(CatalogDescriptor::providerPosition)
					.thenComparingInt(CatalogDescriptor::catalogPosition);

	private final StremioItemGateway items;
	private final StremioSessionCoordinator sessions;
	private final StremioPresentationText text;
	private final Function<BrowseMedia, String> rememberMedia;
	private final StremioLibraryPageLoader.ResumeRecorder resumeRecorder;

	StremioHomePageLoader(StremioItemGateway items, StremioSessionCoordinator sessions,
			StremioPresentationText text, Function<BrowseMedia, String> rememberMedia,
			StremioLibraryPageLoader.ResumeRecorder resumeRecorder) {
		this.items = Objects.requireNonNull(items, "items");
		this.sessions = sessions;
		this.text = Objects.requireNonNull(text, "text");
		this.rememberMedia = Objects.requireNonNull(rememberMedia, "rememberMedia");
		this.resumeRecorder = Objects.requireNonNull(resumeRecorder, "resumeRecorder");
	}

	CompletionStage<StremioPresentationPage> load(StremioPresentationRequest request) {
		StremioPresentationPageBuilder base = new StremioPresentationPageBuilder();
		addHomeActions(base);
		request.publishUpdate(base.build());
		CompletableFuture<List<StremioContinueEntry>> continueItems = (sessions == null) ?
				CompletableFuture.completedFuture(List.of()) :
				request.track(sessions.loadContinue(MAX_SECTION_POSTERS));

		return request.track(items.catalogs()).thenCombine(continueItems,
				HomePayload::new).thenCompose(payload -> {
			request.ensureActive();
			addContinueSection(base, payload.continueItems());
			List<HomeShelf> visible = homeShelves(payload.catalogs());
			if (visible.isEmpty()) {
				base.models.add(StremioPresentationModels.state("state:no-sources",
						text.label(StremioPresentationText.Label.NO_SOURCES),
						StremioUiModel.StateKind.EMPTY));
				return CompletableFuture.completedFuture(base.build());
			}
			HomeShelf first = visible.get(0);
			base.selections.put("action:discover", new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(first.primary()), first.genre(), 0)));
			request.publishUpdate(base.build());

			List<CompletableFuture<Shelf>> shelves = new ArrayList<>();
			for (HomeShelf shelf : visible) shelves.add(loadShelf(request, shelf));
			List<Shelf> completed = new ArrayList<>(
					java.util.Collections.nCopies(shelves.size(), null));
			Object progressLock = new Object();
			for (int i = 0; i < shelves.size(); i++) {
				int index = i;
				shelves.get(i).whenComplete((shelf, failure) -> {
					if ((failure != null) || (shelf == null)) return;
					StremioPresentationPage update;
					synchronized (progressLock) {
						completed.set(index, shelf);
						update = progressPage(base, completed);
					}
					request.publishUpdate(update);
				});
			}
			return all(shelves).thenApply(ignored -> {
				request.ensureActive();
				return completedPage(base, shelves);
			});
		});
	}

	private StremioPresentationPage progressPage(StremioPresentationPageBuilder base,
			List<Shelf> completed) {
		StremioPresentationPageBuilder progress = new StremioPresentationPageBuilder(base);
		for (Shelf value : completed) {
			if ((value != null) && !value.section().posters().isEmpty()) {
				progress.models.add(value.section());
				progress.selections.putAll(value.selections());
			}
		}
		return progress.build();
	}

	private StremioPresentationPage completedPage(StremioPresentationPageBuilder base,
			List<CompletableFuture<Shelf>> shelves) {
		StremioPresentationPageBuilder completed = new StremioPresentationPageBuilder(base);
		int failures = 0;
		for (var shelf : shelves) {
			Shelf value = shelf.join();
			failures += value.failures();
			if ((value != null) && !value.section().posters().isEmpty()) {
				completed.models.add(value.section());
				completed.selections.putAll(value.selections());
			}
		}
		boolean hasContent = completed.models.stream()
				.anyMatch(StremioUiModel.Section.class::isInstance);
		if (!hasContent) {
			completed.models.add(StremioPresentationModels.state("state:no-content",
					text.label(StremioPresentationText.Label.NO_CONTENT),
					(failures > 0) ? StremioUiModel.StateKind.ERROR :
							StremioUiModel.StateKind.EMPTY));
		}
		if (failures > 0) {
			String retryKey = "action:retry-home";
			completed.models.add(new StremioUiModel.Action(retryKey,
					text.action(StremioUiModel.ActionKind.RETRY),
					StremioUiModel.ActionKind.RETRY));
			completed.selections.put(retryKey,
					new StremioSelection.Command(StremioUiModel.ActionKind.RETRY));
		}
		return completed.build();
	}

	private CompletableFuture<Shelf> loadShelf(
			StremioPresentationRequest request, HomeShelf shelf) {
		List<CompletableFuture<CatalogPage>> pages = new ArrayList<>(shelf.catalogs().size());
		for (HomeCatalog catalog : shelf.catalogs()) {
			pages.add(request.track(items.catalog(catalog.descriptor().route(),
					emptyToNull(catalog.genre()), 0))
					.handle((page, failure) -> (failure == null) ? page : null));
		}
		return all(pages).thenApply(ignored -> {
			request.ensureActive();
			List<BrowseMedia> merged = new ArrayList<>();
			int failures = 0;
			for (CompletableFuture<CatalogPage> page : pages) {
				CatalogPage value = page.join();
				if (value != null) merged.addAll(value.items());
				else failures++;
			}
			Shelf section = section(request, shelf.primary(), shelf.title(), merged,
					shelf.catalogs().size() == 1, shelf.genre());
			return new Shelf(section.section(), section.selections(), failures);
		});
	}

	private List<HomeShelf> homeShelves(List<CatalogDescriptor> catalogs) {
		Map<HomeShelfKey, List<HomeCatalog>> featured = new LinkedHashMap<>();
		Map<HomeShelfKey, List<HomeCatalog>> custom = new LinkedHashMap<>();
		catalogs.stream().filter(StremioHomePageLoader::isSupportedCatalog)
				.sorted(CATALOG_ORDER).forEach(catalog -> {
					HomeCatalog homeCatalog = homeCatalog(catalog);
					if (homeCatalog == null) return;
					String type = catalog.route().type().toLowerCase(java.util.Locale.ROOT);
					HomeShelfKind kind = homeShelfKind(catalog);
					String customName = (kind == HomeShelfKind.CUSTOM) ?
							canonicalShelfName(baseCatalogName(catalog)) : "";
					if (customName.isEmpty() && (kind == HomeShelfKind.CUSTOM)) {
						customName = canonicalShelfName(catalog.route().catalogId());
					}
					HomeShelfKey key = new HomeShelfKey(kind, type, customName);
					Map<HomeShelfKey, List<HomeCatalog>> target =
							(kind == HomeShelfKind.CUSTOM) ? custom : featured;
					target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(homeCatalog);
				});
		List<HomeShelf> result = new ArrayList<>(MAX_HOME_SECTIONS);
		for (Map<HomeShelfKey, List<HomeCatalog>> source : List.of(featured, custom)) {
			for (var entry : source.entrySet()) {
				List<HomeCatalog> group = entry.getValue();
				HomeCatalog primary = group.get(0);
				result.add(new HomeShelf(primary.descriptor(),
						homeShelfTitle(entry.getKey(), primary.descriptor()), primary.genre(),
						List.copyOf(group.subList(0,
								Math.min(group.size(), MAX_HOME_PROVIDERS_PER_SHELF)))));
				if (result.size() == MAX_HOME_SECTIONS) return List.copyOf(result);
			}
		}
		return List.copyOf(result);
	}

	private String homeShelfTitle(HomeShelfKey key, CatalogDescriptor primary) {
		StremioPresentationText.Label label = switch (key.kind()) {
			case POPULAR -> key.type().equals("movie") ?
					StremioPresentationText.Label.POPULAR_MOVIES :
					StremioPresentationText.Label.POPULAR_SERIES;
			case NEW -> key.type().equals("movie") ?
					StremioPresentationText.Label.NEW_MOVIES :
					StremioPresentationText.Label.NEW_SERIES;
			case FEATURED -> key.type().equals("movie") ?
					StremioPresentationText.Label.FEATURED_MOVIES :
					StremioPresentationText.Label.FEATURED_SERIES;
			case CUSTOM -> null;
		};
		return (label == null) ? primary.name().trim() : text.label(label);
	}

	private Shelf section(StremioPresentationRequest request, CatalogDescriptor catalog,
			String title, List<BrowseMedia> items, boolean includeSeeAll, String discoverGenre) {
		request.ensureActive();
		String sectionKey = "section:" + catalogKey(catalog);
		List<StremioUiModel.Poster> posters = new ArrayList<>();
		Map<String, StremioSelection> selections = new LinkedHashMap<>();
		for (BrowseMedia media : deduplicate(items, MAX_SECTION_POSTERS)) {
			String detailsKey = rememberMedia.apply(media);
			String key = sectionKey + ":poster:" + detailsKey;
			posters.add(StremioPresentationModels.poster(key, media, 0f));
			selections.put(key,
					new StremioSelection.Navigate(new StremioRoute.Details(detailsKey)));
		}
		StremioUiModel.Action seeAll = null;
		if (includeSeeAll) {
			String seeAllKey = sectionKey + ":see-all";
			seeAll = new StremioUiModel.Action(seeAllKey,
					text.label(StremioPresentationText.Label.SEE_ALL),
					StremioUiModel.ActionKind.DISCOVER);
			selections.put(seeAllKey, new StremioSelection.Navigate(
					new StremioRoute.Discover(catalogKey(catalog), discoverGenre, 0)));
		}
		return new Shelf(new StremioUiModel.Section(sectionKey, title, posters, seeAll),
				Map.copyOf(selections), 0);
	}

	private void addHomeActions(StremioPresentationPageBuilder builder) {
		List<StremioUiModel.Action> actions = new ArrayList<>(4);
		for (StremioUiModel.ActionKind action : List.of(
				StremioUiModel.ActionKind.SEARCH,
				StremioUiModel.ActionKind.DISCOVER,
				StremioUiModel.ActionKind.LIBRARY,
				StremioUiModel.ActionKind.ADDONS)) {
			String key = "action:" + action.name().toLowerCase(java.util.Locale.ROOT);
			actions.add(new StremioUiModel.Action(key, text.action(action), action));
			builder.selections.put(key, new StremioSelection.Command(action));
		}
		builder.models.add(new StremioUiModel.ActionBar("actions:home", actions));
	}

	private void addContinueSection(StremioPresentationPageBuilder builder,
			List<StremioContinueEntry> entries) {
		if (entries.isEmpty()) return;
		List<StremioUiModel.Poster> posters = new ArrayList<>(entries.size());
		for (StremioContinueEntry entry : entries) {
			resumeRecorder.remember(entry.item().stableId(), entry.positionMs(), entry.durationMs());
			String key = "continue:poster:" + entry.item().stableId();
			float progress = Math.max(0f, Math.min(1f,
					(float) entry.positionMs() / (float) entry.durationMs()));
			posters.add(new StremioUiModel.Poster(key, entry.item().title(),
					entry.item().subtitle(), optional(entry.item().artwork()), progress, true));
			builder.selections.put(key, new StremioSelection.Restore(
					entry.item().stableId(), true));
		}
		builder.models.add(new StremioUiModel.Section("section:continue",
				text.label(StremioPresentationText.Label.CONTINUE_WATCHING), posters));
	}

	private static HomeShelfKind homeShelfKind(CatalogDescriptor catalog) {
		String type = catalog.route().type().toLowerCase(java.util.Locale.ROOT);
		if (!type.equals("movie") && !type.equals("series")) return HomeShelfKind.CUSTOM;
		String identity = canonicalShelfName(catalog.name() + ' ' + catalog.route().catalogId());
		if (identity.contains("featured") || identity.contains("imdbrating") ||
				identity.contains("recommended")) return HomeShelfKind.FEATURED;
		if (identity.contains("new") || identity.contains("latest") ||
				identity.contains("recent") || canonicalShelfName(
				catalog.route().catalogId()).equals("year")) return HomeShelfKind.NEW;
		if (identity.contains("popular") || identity.contains("trending") ||
				canonicalShelfName(catalog.route().catalogId()).equals("top")) {
			return HomeShelfKind.POPULAR;
		}
		return HomeShelfKind.CUSTOM;
	}

	private static HomeCatalog homeCatalog(CatalogDescriptor catalog) {
		String genre = "";
		for (var extra : catalog.extras()) {
			if (!extra.required()) continue;
			if (!extra.name().equalsIgnoreCase("genre")) return null;
			genre = defaultRequiredGenre(catalog, extra.options());
			if (genre.isEmpty()) return null;
		}
		return new HomeCatalog(catalog, genre);
	}

	private static String defaultRequiredGenre(
			CatalogDescriptor catalog, List<String> options) {
		List<String> values = catalog.genres().isEmpty() ? options : catalog.genres();
		for (String value : values) {
			if ((value != null) && !value.isBlank()) return value.trim();
		}
		return "";
	}

	private static String baseCatalogName(CatalogDescriptor catalog) {
		String name = catalog.name().trim();
		String stripped = switch (catalog.route().type().toLowerCase(java.util.Locale.ROOT)) {
			case "movie" -> name.replaceFirst(
					"(?i)\\s*(?:[-:|]\\s*)?(?:movies?|films?)\\s*$", "").trim();
			case "series" -> name.replaceFirst(
					"(?i)\\s*(?:[-:|]\\s*)?(?:series|tv\\s*shows?|shows?)\\s*$", "").trim();
			default -> name;
		};
		return stripped.isEmpty() ? name : stripped;
	}

	private static String canonicalShelfName(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
	}

	private static List<BrowseMedia> deduplicate(List<BrowseMedia> items, int limit) {
		Map<String, BrowseMedia> unique = new LinkedHashMap<>();
		for (BrowseMedia media : items) {
			unique.putIfAbsent(StremioItemIds.meta(media), media);
			if (unique.size() == limit) break;
		}
		return List.copyOf(unique.values());
	}

	private static String catalogKey(CatalogDescriptor catalog) {
		return StremioItemIds.catalog(catalog);
	}

	private static boolean isSupportedCatalog(CatalogDescriptor catalog) {
		return !catalog.route().type().equalsIgnoreCase("youtube");
	}

	private static String optional(String value) {
		return Objects.requireNonNullElse(value, "");
	}

	private static String emptyToNull(String value) {
		return ((value == null) || value.isBlank()) ? null : value;
	}

	private static CompletableFuture<Void> all(
			List<? extends CompletableFuture<?>> futures) {
		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
	}

	private record Shelf(StremioUiModel.Section section,
			Map<String, StremioSelection> selections, int failures) {
	}

	private record HomePayload(List<CatalogDescriptor> catalogs,
			List<StremioContinueEntry> continueItems) {
	}

	private record HomeShelf(CatalogDescriptor primary, String title,
			String genre, List<HomeCatalog> catalogs) {
	}

	private record HomeCatalog(CatalogDescriptor descriptor, String genre) {
	}

	private record HomeShelfKey(HomeShelfKind kind, String type, String customName) {
	}

	private enum HomeShelfKind { POPULAR, NEW, FEATURED, CUSTOM }
}
