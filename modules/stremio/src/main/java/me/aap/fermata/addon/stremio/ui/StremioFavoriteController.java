package me.aap.fermata.addon.stremio.ui;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.presentation.StremioPresentationGateway;
import me.aap.fermata.addon.stremio.presentation.StremioPresenter;
import me.aap.fermata.addon.stremio.presentation.StremioUiModel;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;

/** Serializes favorite changes while the host validates current presenter/gateway ownership. */
public final class StremioFavoriteController {
	public interface Host {
		StremioPresenter presenter();
		StremioPresentationGateway gateway();
		FutureSupplier<Void> setFavorite(DefaultMediaLib lib, PlayableItem item, boolean favorite);
	}

	private String operation;

	public void toggle(Host host, MainActivityDelegate activity,
			StremioUiModel.DetailsHeader details) {
		StremioPresentationGateway gateway = host.gateway();
		StremioPresenter presenter = host.presenter();
		if ((gateway == null) || (presenter == null)) return;
		StremioPresentationGateway.FavoriteTarget target =
				gateway.favoriteTarget(details.stableKey());
		if ((target == null) || (operation != null)) return;
		operation = target.stableId();
		DefaultMediaLib lib = (DefaultMediaLib) activity.getLib();
		FutureSupplier<? extends Item> resolved =
				lib.getItem(target.stableId()).main(activity.getHandler());
		resolved.onSuccess(item -> {
			if ((host.presenter() != presenter) || (host.gateway() != gateway)) {
				clear(target.stableId());
				return;
			}
			if (!(item instanceof PlayableItem playable)) {
				clear(target.stableId());
				UiUtils.showAlert(activity.getContext(), R.string.stremio_favorite_unavailable);
				return;
			}
			FutureSupplier<Void> update = host.setFavorite(
					lib, playable, !target.favorite()).main(activity.getHandler());
			update.onSuccess(ignored -> {
				clear(target.stableId());
				if ((host.presenter() == presenter) && (host.gateway() == gateway)) {
					presenter.refresh();
				}
			});
			update.onFailure(error -> {
				clear(target.stableId());
				UiUtils.showAlert(activity.getContext(), R.string.stremio_favorite_unavailable);
			});
		});
		resolved.onFailure(error -> {
			clear(target.stableId());
			UiUtils.showAlert(activity.getContext(), R.string.stremio_favorite_unavailable);
		});
	}

	public void clear() {
		operation = null;
	}

	private void clear(String stableId) {
		if (stableId.equals(operation)) operation = null;
	}
}
