package me.aap.fermata.addon.stremio.ui;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.StremioAddon;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.StremioBrowseTarget;
import me.aap.fermata.addon.stremio.item.StremioEpisodeItem;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioMetaItem;
import me.aap.fermata.addon.stremio.item.StremioPlaybackItemFactory;
import me.aap.fermata.addon.stremio.item.StremioPlaybackSelection;
import me.aap.fermata.addon.stremio.item.StremioSeasonItem;
import me.aap.fermata.addon.stremio.item.StremioStreamPickerItem;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationGateway;
import me.aap.fermata.addon.stremio.presentation.StremioPresenter;
import me.aap.fermata.addon.stremio.presentation.StremioRoute;
import me.aap.fermata.addon.stremio.runtime.StremioRuntimeGraph;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;

/** Resolves persistent items and hands canonical playback/navigation back to the fragment host. */
public final class StremioPlaybackHandoffController {
	public interface Host {
		StremioRuntimeGraph graph();
		StremioPresentationGateway gateway();
		StremioPresenter presenter();
		MainActivityDelegate activity();
		StremioAddon addon();
		void openCanonicalPath(BrowseMedia media, BrowseEpisode episode, BrowseSeason season,
				boolean streams, String title, StremioRoute origin);
		void releaseSelection(String key);
	}

	private FutureSupplier<StremioBrowseTarget> targetResolution;
	private long targetResolutionGeneration;
	private Item pendingItem;

	public void play(Host host, String key) {
		StremioPresentationGateway gateway = host.gateway();
		StremioRuntimeGraph graph = host.graph();
		StremioPresenter presenter = host.presenter();
		StremioPlaybackSelection selection = (gateway == null) ? null : gateway.playbackTarget(key);
		if ((selection == null) || (graph == null)) {
			host.releaseSelection(key);
			if (presenter != null) presenter.refresh();
			return;
		}
		try {
			DefaultMediaLib lib = (DefaultMediaLib) host.activity().getLib();
			StremioPlaybackItemFactory.Result result = StremioPlaybackItemFactory.create(
					host.addon().getRootItem(lib), graph.items(), selection);
			host.activity().playItem(result.playable());
		} catch (RuntimeException failure) {
			host.releaseSelection(key);
			UiUtils.showAlert(host.activity().getContext(), R.string.stremio_source_error_unknown);
		}
	}

	public void showMediaItem(Host host, Item item) {
		if ((host.presenter() == null) || (host.gateway() == null) || (host.graph() == null)) {
			pendingItem = item;
			return;
		}
		cancelTargetResolution();
		if (item instanceof StremioMetaItem meta) {
			host.openCanonicalPath(meta.media(), null, null, false, meta.getName(), null);
			return;
		}
		if (item instanceof StremioSeasonItem season) {
			host.openCanonicalPath(season.media(), null, season.season(), false,
					season.media().title(), null);
			return;
		}
		if (item instanceof StremioEpisodeItem episode &&
				episode.getParent() instanceof StremioSeasonItem season) {
			host.openCanonicalPath(episode.media(), episode.episode(), season.season(), true,
					episode.getName(), null);
			return;
		}
		String stableId;
		if (item instanceof StremioStreamPickerItem picker) {
			stableId = picker.request().identity().videoKey();
		} else if (item instanceof PlayableItem playable) {
			stableId = playable.getOrigId();
		} else {
			stableId = item.getId();
		}
		if (item instanceof PlaybackProgressItem progress) {
			host.gateway().rememberResume(stableId, progress.getResumePosition(), -1L);
		}
		resolvePersistentTarget(host, stableId, !(item instanceof StremioMetaItem), stableId, null);
	}

	public void resolvePersistentTarget(Host host, String stableId, boolean streams,
			String interactionKey, StremioRoute origin) {
		cancelTargetResolution();
		StremioRuntimeGraph runtime = host.graph();
		StremioPresentationGateway gateway = host.gateway();
		if ((runtime == null) || (gateway == null)) {
			host.releaseSelection(interactionKey);
			return;
		}
		long generation = ++targetResolutionGeneration;
		FutureSupplier<StremioBrowseTarget> resolution = runtime.items()
				.presentationTarget(stableId).main(host.activity().getHandler());
		targetResolution = resolution;
		resolution.onSuccess(target -> {
			if ((targetResolution != resolution) || (generation != targetResolutionGeneration) ||
					(host.graph() != runtime) || (host.gateway() != gateway)) return;
			targetResolution = null;
			if ((target == null) || (host.presenter() == null)) {
				host.releaseSelection(interactionKey);
				return;
			}
			String routeId = (target.episode() == null) ?
					StremioItemIds.meta(target.media()) : StremioItemIds.episode(target.episode());
			gateway.transferResume(stableId, routeId);
			host.openCanonicalPath(target.media(), target.episode(), target.season(), streams,
					targetTitle(target), origin);
		}).onFailure(error -> {
			if (targetResolution != resolution) return;
			targetResolution = null;
			host.releaseSelection(interactionKey);
		});
	}

	public Item takePendingItem() {
		Item item = pendingItem;
		pendingItem = null;
		return item;
	}

	public void clearView() {
		cancelTargetResolution();
	}

	public void clear() {
		cancelTargetResolution();
		pendingItem = null;
	}

	private void cancelTargetResolution() {
		targetResolutionGeneration++;
		FutureSupplier<StremioBrowseTarget> resolution = targetResolution;
		targetResolution = null;
		if (resolution != null) resolution.cancel();
	}

	private static String targetTitle(StremioBrowseTarget target) {
		return (target.episode() == null) ? target.media().title() : target.episode().title();
	}
}
