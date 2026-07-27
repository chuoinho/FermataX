package me.aap.fermata.addon.stremio.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationGateway;
import me.aap.fermata.addon.stremio.presentation.StremioPresenter;
import me.aap.fermata.addon.stremio.presentation.StremioRoute;
import me.aap.fermata.addon.stremio.presentation.StremioScreenState;
import me.aap.fermata.addon.stremio.presentation.StremioUiModel;

/** Owns route titles and route-stack mutations while the fragment owns the presenter lifecycle. */
public final class StremioNavigationController {
	private static final int MAX_ROUTE_TITLES = 24;

	private final Map<StremioRoute, String> routeTitles =
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<StremioRoute, String> eldest) {
					return size() > MAX_ROUTE_TITLES;
				}
			};

	public String title(StremioScreenState state) {
		if (state == null) return null;
		return routeTitles.get(state.route());
	}

	public static String detailsTitle(List<StremioUiModel> models) {
		for (StremioUiModel model : models) {
			if (model instanceof StremioUiModel.DetailsHeader details) return details.title();
		}
		return null;
	}

	public void navigate(StremioPresenter presenter, StremioRoute route, String title,
			Runnable saveViewport) {
		saveViewport.run();
		remember(route, title);
		presenter.navigate(route);
	}

	public void replace(StremioPresenter presenter, StremioScreenState state, StremioRoute route,
			String title, boolean preserveViewport, Runnable saveViewport) {
		saveViewport.run();
		if (((title == null) || title.isBlank()) && (state != null)) {
			title = routeTitles.get(state.route());
		}
		remember(route, title);
		presenter.replace(route, preserveViewport);
	}

	public void openCanonicalPath(StremioPresenter presenter, StremioPresentationGateway gateway,
			BrowseMedia media, BrowseEpisode episode, BrowseSeason season, boolean streams,
			String title, StremioRoute origin) {
		StremioRoute.Details base = gateway.adopt(media);
		StremioRoute.Details details = (season == null) ? base :
				new StremioRoute.Details(base.stableId(), season.number());
		remember(details, media.title());
		List<StremioRoute> path = new ArrayList<>();
		path.add(new StremioRoute.Home());
		if ((origin instanceof StremioRoute.Library) ||
				(origin instanceof StremioRoute.Search) ||
				(origin instanceof StremioRoute.Discover)) path.add(origin);
		path.add(details);
		if (streams) {
			StremioRoute.Streams streamRoute = gateway.adopt(media, episode, season);
			remember(streamRoute, title);
			path.add(streamRoute);
		}
		presenter.openPath(path);
	}

	public void clear() {
		routeTitles.clear();
	}

	public static String modelTitle(String key, List<StremioUiModel> models) {
		for (StremioUiModel model : models) {
			if (model.stableKey().equals(key)) {
				if (model instanceof StremioUiModel.Poster poster) return poster.title();
				if (model instanceof StremioUiModel.Episode episode) return episode.title();
				if (model instanceof StremioUiModel.Action action) return action.title();
				if (model instanceof StremioUiModel.DetailsHeader details) return details.title();
			}
			if (model instanceof StremioUiModel.Section section) {
				StremioUiModel.Action seeAll = section.seeAll();
				if ((seeAll != null) && seeAll.stableKey().equals(key)) return section.title();
				for (StremioUiModel.Poster poster : section.posters()) {
					if (poster.stableKey().equals(key)) return poster.title();
				}
			}
		}
		return "";
	}

	private void remember(StremioRoute route, String title) {
		if ((title != null) && !title.isBlank()) routeTitles.put(route, title);
	}
}
