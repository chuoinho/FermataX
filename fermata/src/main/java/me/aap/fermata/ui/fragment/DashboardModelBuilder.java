package me.aap.fermata.ui.fragment;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.utils.pref.PreferenceStore;

final class DashboardModelBuilder {
	private final Context ctx;
	private final PreferenceStore store;

	DashboardModelBuilder(Context ctx, PreferenceStore store) {
		this.ctx = ctx;
		this.store = store;
	}

	void rebuild(List<DashboardCard> cards, @Nullable DashboardCard smartTopCard) {
		cards.clear();
		if (smartTopCard != null) cards.add(smartTopCard);
		for (DashboardItems.Item item : DashboardItems.getDashboardItems(ctx, store)) {
			if (item.id == R.id.recent_fragment) continue;
			if ((smartTopCard != null) && (smartTopCard.targetId == item.id)) continue;
			cards.add(DashboardCard.item(item));
		}
	}

	List<DashboardItems.Item> getDashboardItems(List<DashboardCard> cards) {
		List<DashboardItems.Item> items = new ArrayList<>(cards.size());
		for (DashboardCard card : cards) {
			if (card.item != null) items.add(card.item);
		}
		return items;
	}
}
