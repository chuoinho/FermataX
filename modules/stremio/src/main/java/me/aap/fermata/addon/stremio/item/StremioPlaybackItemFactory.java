package me.aap.fermata.addon.stremio.item;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** Reuses the existing item/playback contracts behind the native Stremio renderer. */
public final class StremioPlaybackItemFactory {
	private StremioPlaybackItemFactory() {
	}

	public static Result create(StremioRootItem root, StremioItemGateway gateway,
			StremioPlaybackSelection selection) {
		StremioMetaItem meta = new StremioMetaItem(root, root, gateway, selection.media());
		BrowsableItem contentParent = meta;
		if (selection.episode() != null) {
			StremioSeasonItem season = new StremioSeasonItem(meta, root, gateway,
					selection.media(), selection.season());
			contentParent = new StremioEpisodeItem(season, root, gateway,
					selection.media(), selection.episode());
		}
		StremioStreamPickerItem picker = new StremioStreamPickerItem(contentParent, root,
				gateway, selection.media(), selection.episode());
		PlayableItem playable = new StremioDirectPlayableItem(picker, gateway,
				selection.descriptor(), selection.request(), selection.resumePositionMs());
		return new Result(playable, picker);
	}

	public record Result(PlayableItem playable, StremioStreamPickerItem picker) {
	}
}
