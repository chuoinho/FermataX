package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.utils.pref.PreferenceStore;

final class DashboardModelBuilder {
	private static final float AUTOMOTIVE_ADDON_TITLE_SCALE = 1.15F;
	private final Context ctx;
	private final PreferenceStore store;
	private final boolean automotive;
	private final boolean smartTopVisible;

	DashboardModelBuilder(Context ctx, PreferenceStore store, boolean automotive) {
		this.ctx = ctx;
		this.store = store;
		this.automotive = automotive;
		smartTopVisible = shouldShowSmartTop(automotive);
	}

	void rebuild(List<DashboardCard> cards, @Nullable DashboardCard smartTopCard) {
		cards.clear();
		DashboardCard visibleSmartTop = smartTopVisible ? smartTopCard : null;
		if (visibleSmartTop != null) cards.add(visibleSmartTop);
		for (DashboardItems.Item item : DashboardItems.getDashboardItems(ctx, store)) {
			if (shouldSuppressRecent(visibleSmartTop != null, item.id)) continue;
			if ((visibleSmartTop != null) && (visibleSmartTop.targetId == item.id)) continue;
			DashboardCard card = DashboardCard.item(item);
			if (automotive && (item.addonInfo != null)) card = card.withTitle(emphasizeAddonTitle(card.title));
			cards.add(card);
		}
	}

	List<DashboardItems.Item> getDashboardItems(List<DashboardCard> cards) {
		List<DashboardItems.Item> items = new ArrayList<>(cards.size());
		for (DashboardCard card : cards) {
			if (card.item != null) items.add(card.item);
		}
		return items;
	}

	static boolean shouldShowSmartTop(boolean automotive) {
		return automotive;
	}

	static boolean shouldSuppressRecent(boolean smartTopVisible, int itemId) {
		return smartTopVisible && (itemId == R.id.recent_fragment);
	}

	static float addonTitleScale(boolean automotive, boolean addon) {
		return automotive && addon ? AUTOMOTIVE_ADDON_TITLE_SCALE : 1F;
	}

	private static CharSequence emphasizeAddonTitle(CharSequence title) {
		SpannableString text = new SpannableString(title);
		text.setSpan(new RelativeSizeSpan(AUTOMOTIVE_ADDON_TITLE_SCALE), 0, text.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return text;
	}
}
