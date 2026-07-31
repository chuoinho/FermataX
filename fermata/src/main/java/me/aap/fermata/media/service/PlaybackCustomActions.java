package me.aap.fermata.media.service;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.collection.CollectionUtils;

final class PlaybackCustomActions {
	static final String REWIND = "me.aap.fermata.action.rewind";
	static final String FAST_FORWARD = "me.aap.fermata.action.fastForward";
	static final String REPEAT_ENABLE = "me.aap.fermata.action.repeat.enable";
	static final String REPEAT_DISABLE = "me.aap.fermata.action.repeat.disable";
	static final String SHUFFLE_ENABLE = "me.aap.fermata.action.shuffle.enable";
	static final String SHUFFLE_DISABLE = "me.aap.fermata.action.shuffle.disable";
	static final String FAVORITES_ADD = "me.aap.fermata.action.favorites.add";
	static final String FAVORITES_REMOVE = "me.aap.fermata.action.favorites.remove";

	private final PlaybackStateCompat.CustomAction rewind;
	private final PlaybackStateCompat.CustomAction fastForward;
	private final PlaybackStateCompat.CustomAction repeatEnable;
	private final PlaybackStateCompat.CustomAction repeatDisable;
	private final PlaybackStateCompat.CustomAction shuffleEnable;
	private final PlaybackStateCompat.CustomAction shuffleDisable;
	private final PlaybackStateCompat.CustomAction favoritesAdd;
	private final PlaybackStateCompat.CustomAction favoritesRemove;

	PlaybackCustomActions(Context context) {
		this(action(REWIND, context.getString(R.string.rewind), R.drawable.rw),
				action(FAST_FORWARD, context.getString(R.string.fast_forward), R.drawable.ff),
				action(REPEAT_ENABLE, context.getString(R.string.repeat), R.drawable.repeat),
				action(REPEAT_DISABLE, context.getString(R.string.repeat_disable),
						R.drawable.repeat_filled),
				action(SHUFFLE_ENABLE, context.getString(R.string.shuffle), R.drawable.shuffle),
				action(SHUFFLE_DISABLE, context.getString(R.string.shuffle_disable),
						R.drawable.shuffle_filled),
				action(FAVORITES_ADD, context.getString(R.string.favorites_add), R.drawable.favorite),
				action(FAVORITES_REMOVE, context.getString(R.string.favorites_remove),
						R.drawable.favorite_filled));
	}

	PlaybackCustomActions(PlaybackStateCompat.CustomAction rewind,
			PlaybackStateCompat.CustomAction fastForward,
			PlaybackStateCompat.CustomAction repeatEnable,
			PlaybackStateCompat.CustomAction repeatDisable,
			PlaybackStateCompat.CustomAction shuffleEnable,
			PlaybackStateCompat.CustomAction shuffleDisable,
			PlaybackStateCompat.CustomAction favoritesAdd,
			PlaybackStateCompat.CustomAction favoritesRemove) {
		this.rewind = rewind;
		this.fastForward = fastForward;
		this.repeatEnable = repeatEnable;
		this.repeatDisable = repeatDisable;
		this.shuffleEnable = shuffleEnable;
		this.shuffleDisable = shuffleDisable;
		this.favoritesAdd = favoritesAdd;
		this.favoritesRemove = favoritesRemove;
	}

	void setRepeatEnabled(List<PlaybackStateCompat.CustomAction> actions, boolean enabled) {
		CollectionUtils.replace(actions, enabled ? repeatEnable : repeatDisable,
				enabled ? repeatDisable : repeatEnable);
	}

	void setShuffleEnabled(List<PlaybackStateCompat.CustomAction> actions, boolean enabled) {
		CollectionUtils.replace(actions, enabled ? shuffleEnable : shuffleDisable,
				enabled ? shuffleDisable : shuffleEnable);
	}

	void setFavorite(List<PlaybackStateCompat.CustomAction> actions, boolean favorite) {
		CollectionUtils.replace(actions, favorite ? favoritesAdd : favoritesRemove,
				favorite ? favoritesRemove : favoritesAdd);
	}

	PlaybackStateCompat createPlayingState(PlayableItem item, int state, long queueId,
			long position, float speed, long supportedActions) {
		BrowsableItemPrefs prefs = item.getParent().getPrefs();
		boolean repeat = prefs.getRepeatPref();
		boolean shuffle = prefs.getShufflePref();
		return new PlaybackStateCompat.Builder().setActions(supportedActions)
				.setState(state, position, speed).setActiveQueueItemId(queueId)
				.addCustomAction(rewind).addCustomAction(fastForward)
				.addCustomAction(repeat ? repeatDisable : repeatEnable)
				.addCustomAction(shuffle ? shuffleDisable : shuffleEnable)
				.addCustomAction(item.isFavoriteItem() ? favoritesRemove : favoritesAdd).build();
	}

	private static PlaybackStateCompat.CustomAction action(String id, String name, int icon) {
		return new PlaybackStateCompat.CustomAction.Builder(id, name, icon).build();
	}
}
