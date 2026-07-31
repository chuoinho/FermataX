package me.aap.fermata.media.service;

import static org.junit.Assert.assertSame;

import android.support.v4.media.session.PlaybackStateCompat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PlaybackCustomActionsTest {
	@Test
	public void togglesReplaceOnlyTheOwnedAction() {
		PlaybackStateCompat.CustomAction rewind = action(PlaybackCustomActions.REWIND);
		PlaybackStateCompat.CustomAction fastForward = action(PlaybackCustomActions.FAST_FORWARD);
		PlaybackStateCompat.CustomAction repeatOn = action(PlaybackCustomActions.REPEAT_ENABLE);
		PlaybackStateCompat.CustomAction repeatOff = action(PlaybackCustomActions.REPEAT_DISABLE);
		PlaybackStateCompat.CustomAction shuffleOn = action(PlaybackCustomActions.SHUFFLE_ENABLE);
		PlaybackStateCompat.CustomAction shuffleOff = action(PlaybackCustomActions.SHUFFLE_DISABLE);
		PlaybackStateCompat.CustomAction favoriteOn = action(PlaybackCustomActions.FAVORITES_ADD);
		PlaybackStateCompat.CustomAction favoriteOff = action(PlaybackCustomActions.FAVORITES_REMOVE);
		PlaybackCustomActions actions = new PlaybackCustomActions(rewind, fastForward,
				repeatOn, repeatOff, shuffleOn, shuffleOff, favoriteOn, favoriteOff);
		List<PlaybackStateCompat.CustomAction> state = new ArrayList<>(
				List.of(rewind, repeatOn, shuffleOn, favoriteOn));

		actions.setRepeatEnabled(state, true);
		actions.setShuffleEnabled(state, true);
		actions.setFavorite(state, true);

		assertSame(rewind, state.get(0));
		assertSame(repeatOff, state.get(1));
		assertSame(shuffleOff, state.get(2));
		assertSame(favoriteOff, state.get(3));

		actions.setRepeatEnabled(state, false);
		actions.setShuffleEnabled(state, false);
		actions.setFavorite(state, false);
		assertSame(repeatOn, state.get(1));
		assertSame(shuffleOn, state.get(2));
		assertSame(favoriteOn, state.get(3));
	}

	private static PlaybackStateCompat.CustomAction action(String id) {
		return new PlaybackStateCompat.CustomAction.Builder(id, id, 1).build();
	}
}
