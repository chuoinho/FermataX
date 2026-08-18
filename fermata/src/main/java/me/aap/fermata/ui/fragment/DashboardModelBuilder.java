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

	DashboardModelBuilder(Context ctx, PreferenceStore store, boolean automotive) {
		this.ctx = ctx;
		this.store = store;
		this.automotive = automotive;
	}

	void rebuild(List<DashboardCard> cards, @Nullable DashboardCard smartTopCard) {
		cards.clear();
		if (smartTopCard != null) cards.add(smartTopCard);
		for (DashboardItems.Item item : DashboardItems.getDashboardItems(ctx, store)) {
			if (item.id == R.id.recent_fragment) continue;
			if ((smartTopCard != null) && (smartTopCard.targetId == item.id)) continue;
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
