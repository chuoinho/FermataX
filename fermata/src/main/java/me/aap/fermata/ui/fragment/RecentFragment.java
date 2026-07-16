package me.aap.fermata.ui.fragment;

import android.view.View;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Recent;
import me.aap.fermata.media.pref.RecentPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemView;
import me.aap.utils.pref.PreferenceStore;

public class RecentFragment extends MediaLibFragment {

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new RecentAdapter(getMainActivity(), b.getLib().getRecent());
	}

	@Override
	public int getFragmentId() {
		return R.id.recent_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.recent);
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRecent().isRecentItemId(i.getId());
	}

	private Recent getRecent() {
		return getLib().getRecent();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		RecentAdapter a = getAdapter();
		if (!a.isCallbackCall() && prefs.contains(RecentPrefs.RECENT)) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private class RecentAdapter extends ListAdapter {

		RecentAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
		}

		@Override
		public void onClick(View view) {
			if ((view instanceof MediaItemView itemView) &&
					(itemView.getItem() instanceof PlayableItem playable)) {
				discardSelection();
				DashboardPlayableNavigator.playAndGoToPlayable(getMainActivity(), playable);
				return;
			}
			super.onClick(view);
		}

		@Override
		protected void onItemDismiss(int position) {
			getRecent().removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			getRecent().moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}
	}
}
