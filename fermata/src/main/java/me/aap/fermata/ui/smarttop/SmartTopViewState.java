package me.aap.fermata.ui.smarttop;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.addon.SmartTopProviderResult;

/** Immutable renderer input. It carries presentation and canonical ownership separately. */
public record SmartTopViewState(
		long generation,
		SmartTopMode mode,
		SmartTopLayoutMode layout,
		@Nullable PlayableItem presentedItem,
		@Nullable PlayableItem canonicalItem,
		@DrawableRes int icon,
		CharSequence eyebrow,
		CharSequence title,
		CharSequence subtitle,
		SmartTopTimeline timeline,
		SmartTopCapabilities capabilities,
		List<SmartTopAction> actions,
		boolean favorite,
		List<PlayableItem> quickRecent,
		@Nullable SmartTopProviderResult providerResult) {
	public SmartTopViewState {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(layout, "layout");
		Objects.requireNonNull(eyebrow, "eyebrow");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(subtitle, "subtitle");
		Objects.requireNonNull(timeline, "timeline");
		Objects.requireNonNull(capabilities, "capabilities");
		actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
		quickRecent = List.copyOf(Objects.requireNonNull(quickRecent, "quickRecent"));
		if (quickRecent.size() > 1) throw new IllegalArgumentException("At most one Quick Recent item");
		if ((mode == SmartTopMode.CURRENT) && (canonicalItem == null)) {
			throw new IllegalArgumentException("Current state requires canonical ownership");
		}
	}

	public SmartTopViewState withLayout(SmartTopLayoutMode nextLayout) {
		if (layout == nextLayout) return this;
		return new SmartTopViewState(generation, mode, nextLayout, presentedItem, canonicalItem,
				icon, eyebrow, title, subtitle, timeline, capabilities,
				SmartTopActionPolicy.resolve(mode, nextLayout, capabilities), favorite,
				(nextLayout == SmartTopLayoutMode.COMPACT) ? List.of() : quickRecent, providerResult);
	}

	public SmartTopViewState withTitle(CharSequence nextTitle) {
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, eyebrow, nextTitle, subtitle, timeline, capabilities, actions,
				favorite, quickRecent, providerResult);
	}

	public SmartTopViewState withQuickRecent(List<PlayableItem> recent) {
		List<PlayableItem> bounded = (layout == SmartTopLayoutMode.COMPACT) || recent.isEmpty() ?
				List.of() : List.of(recent.get(0));
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, eyebrow, title, subtitle, timeline, capabilities, actions,
				favorite, bounded, providerResult);
	}

	public SmartTopViewState withTimeline(SmartTopTimeline nextTimeline) {
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, eyebrow, title, subtitle, nextTimeline, capabilities, actions,
				favorite, quickRecent, providerResult);
	}
}
