package me.aap.fermata.ui.smarttop;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.addon.SmartTopProviderResult;

/** Immutable renderer input. Semantic content survives viewport changes unchanged. */
public record SmartTopViewState(
		long generation,
		SmartTopMode mode,
		SmartTopLayoutMode layout,
		@Nullable PlayableItem presentedItem,
		@Nullable PlayableItem canonicalItem,
		@DrawableRes int icon,
		SmartTopBackground background,
		CharSequence eyebrow,
		CharSequence title,
		CharSequence subtitle,
		SmartTopTimeline timeline,
		SmartTopCapabilities capabilities,
		List<SmartTopAction> actions,
		boolean favorite,
		List<PlayableItem> quickRecent,
		@Nullable SmartTopProviderResult providerResult) {
	public static final int MAX_QUICK_RECENT = 3;

	public SmartTopViewState {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(layout, "layout");
		Objects.requireNonNull(background, "background");
		Objects.requireNonNull(eyebrow, "eyebrow");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(subtitle, "subtitle");
		Objects.requireNonNull(timeline, "timeline");
		Objects.requireNonNull(capabilities, "capabilities");
		actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
		quickRecent = List.copyOf(Objects.requireNonNull(quickRecent, "quickRecent"));
		if (quickRecent.size() > MAX_QUICK_RECENT) {
			throw new IllegalArgumentException("At most three Quick Recent items");
		}
		if ((mode == SmartTopMode.CURRENT) && (canonicalItem == null)) {
			throw new IllegalArgumentException("Current state requires canonical ownership");
		}
	}

	/** Compatibility-only layout mutation. It must never prune semantic actions or Recent data. */
	public SmartTopViewState withLayout(SmartTopLayoutMode nextLayout) {
		if (layout == nextLayout) return this;
		return new SmartTopViewState(generation, mode, nextLayout, presentedItem, canonicalItem,
				icon, background, eyebrow, title, subtitle, timeline, capabilities, actions, favorite,
				quickRecent, providerResult);
	}

	public SmartTopViewState withTitle(CharSequence nextTitle) {
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, background, eyebrow, nextTitle, subtitle, timeline, capabilities, actions,
				favorite, quickRecent, providerResult);
	}

	public SmartTopViewState withBackground(SmartTopBackground nextBackground) {
		if (background.equals(nextBackground)) return this;
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, nextBackground, eyebrow, title, subtitle, timeline, capabilities, actions,
				favorite, quickRecent, providerResult);
	}

	public SmartTopViewState withQuickRecent(List<PlayableItem> recent) {
		List<PlayableItem> bounded = recent.isEmpty() ? List.of() :
				List.copyOf(recent.subList(0, Math.min(MAX_QUICK_RECENT, recent.size())));
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, background, eyebrow, title, subtitle, timeline, capabilities, actions,
				favorite, bounded, providerResult);
	}

	public SmartTopViewState withTimeline(SmartTopTimeline nextTimeline) {
		return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
				icon, background, eyebrow, title, subtitle, nextTimeline, capabilities, actions,
				favorite, quickRecent, providerResult);
	}
}
