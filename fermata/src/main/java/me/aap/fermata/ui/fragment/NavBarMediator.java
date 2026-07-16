package me.aap.fermata.ui.fragment;

import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static me.aap.fermata.BuildConfig.VERSION_CODE;
import static me.aap.fermata.BuildConfig.VERSION_NAME;
import static me.aap.utils.ui.UiUtils.showInfo;
import static me.aap.utils.ui.view.NavBarItem.create;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.BackNavigationPolicy;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.fermata.util.Utils;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.GenericFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.NavBarItem;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;
import me.aap.utils.ui.view.PrefNavBarMediator;
import me.aap.utils.ui.view.ScalableTextView;

/**
 * @author Andrey Pavlenko
 */
public class NavBarMediator extends PrefNavBarMediator
		implements AddonManager.Listener, OverlayMenu.SelectionHandler {
	private static final String DONATE_URL = "https://ko-fi.com/fermatax/";

	@Override
	protected List<NavBarItem> getItems(NavBarView nb) {
		List<DashboardItems.NavItem> navItems = DashboardItems.getNavItems(getPreferenceStore(nb));
		List<NavBarItem> items = new ArrayList<>(navItems.size());
		Context ctx = MainActivityDelegate.get(nb.getContext()).getLocalizedContext(nb.getContext());

		for (DashboardItems.NavItem item : navItems) {
			items.add(create(ctx, item.id, item.icon, item.title, true));
		}

		return items;
	}

	@Override
	protected boolean canSwap(NavBarView nb) {
		return false;
	}

	@Override
	protected CharSequence getText(NavBarView nb, @StringRes int text) {
		return MainActivityDelegate.get(nb.getContext()).getLocalizedContext(nb.getContext())
				.getString(text);
	}

	@Override
	protected boolean swap(NavBarView nb, @IdRes int id1, @IdRes int id2) {
		String name1 = DashboardItems.idToName(id1);
		String name2 = DashboardItems.idToName(id2);

		if ((name1 != null) && (name2 != null) &&
				DashboardItems.swap(getPreferenceStore(nb), name1, name2)) return true;

		Log.e("Unable to swap ", name1, " and ", name2);
		return false;
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		FermataApplication.get().getAddonManager().addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		FermataApplication.get().getAddonManager().removeBroadcastListener(this);
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		NavBarView nb = navBar;
		if (nb != null) reload(nb);
	}

	@Override
	public void reload(NavBarView nb) {
		super.reload(nb);
	}

	@Override
	protected PreferenceStore getPreferenceStore(NavBarView nb) {
		return MainActivityDelegate.get(nb.getContext()).getPrefs();
	}

	@Override
	protected Pref<Supplier<String[]>> getPref(NavBarView nb) {
		return DashboardItems.PREF;
	}

	@Override
	public void itemSelected(View item, int id, ActivityDelegate a) {
		if (id == R.id.menu) {
			showMenu(MainActivityDelegate.get(item.getContext()));
		} else if (id == R.id.dashboard_fragment) {
			MainActivityDelegate.get(item.getContext()).showDashboard();
		} else {
			MainActivityDelegate ma = MainActivityDelegate.get(item.getContext());
			if (ma.showFragmentWhenReady(id)) {
				selectOnly(ma.getNavBar(), item);
				ma.setActiveNavItemId(id);
			}
		}
	}

	@Override
	public void fragmentChanged(NavBarView nb, ActivityDelegate a, ActivityFragment f) {
		super.fragmentChanged(nb, a, f);
		View active = nb.findViewById(a.getActiveNavItemId());
		if (active != null) selectOnly(nb, active);
	}

	private static void selectOnly(NavBarView nb, View selected) {
		for (int i = 0, count = nb.getChildCount(); i < count; i++) {
			View child = nb.getChildAt(i);
			child.setSelected(child == selected);
		}
	}

	@Override
	protected boolean extItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.menu) {
			NavButtonView.Ext ext = getExtButton();

			if ((ext != null) && !ext.isSelected()) {
				NavBarItem i = item.getData();
				setExtButton(null, i);
			}

			showMenu(MainActivityDelegate.get(item.getContext()));
			return true;
		} else {
			return super.extItemSelected(item);
		}
	}

	@Override
	public void itemReselected(View item, int id, ActivityDelegate a) {
		MainActivityDelegate ma = (MainActivityDelegate) a;
		if (BackNavigationPolicy.leaveVideoMode(ma)) return;
		if (id == R.id.dashboard_fragment) {
			ma.showDashboard();
			return;
		}
		super.itemReselected(item, id, a);
	}

	@Nullable
	@Override
	public View focusSearch(NavBarView nb, View focused, int direction) {
		if (direction == FOCUS_RIGHT) {
			if (nb.isLeft()) return MediaItemListView.focusSearchActive(nb.getContext(), focused);
		} else if (direction == FOCUS_LEFT) {
			if (nb.isRight()) return MediaItemListView.focusSearchActive(nb.getContext(), focused);
		}

		return null;
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu(MainActivityDelegate.get(nb.getContext()));
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.show(b -> {
			b.setSelectionHandler(this);

			if (a.hasCurrent())
				b.addItem(R.id.nav_got_to_current, R.drawable.go_to_current, R.string.got_to_current);

			ActivityFragment f = a.getActiveFragment();
			if (f instanceof MainActivityFragment) ((MainActivityFragment) f).contributeToNavBarMenu(b);

			b.addItem(R.id.nav_about, R.drawable.about, R.string.about);
			b.addItem(R.id.settings_fragment, R.drawable.settings, R.string.settings);
			if (!a.isCarActivityNotMirror()) b.addItem(R.id.nav_exit, R.drawable.exit, R.string.exit);

			if (BuildConfig.AUTO) b.addItem(R.id.nav_donate, R.drawable.coffee, R.string.donate);
		});
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.nav_got_to_current) {
			MainActivityDelegate.get(item.getContext()).goToCurrent();
			return true;
		} else if (itemId == R.id.nav_about) {
			MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
			if (!(a.showFragment(me.aap.utils.R.id.generic_fragment) instanceof GenericFragment f))
				return false;
			f.setTitle(item.getContext().getString(R.string.about));
			f.setContentProvider(g -> {
				Context ctx = g.getContext();
				ScalableTextView v = new ScalableTextView(ctx);
				String url = "https://github.com/chuoinho/FermataX";
				String html = ctx.getString(R.string.about_html, VERSION_NAME, VERSION_CODE, url);
				int pad = UiUtils.toIntPx(ctx, 10);
				v.setPadding(pad, pad, pad, pad);
				v.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY));
				v.setOnClickListener(t -> openUrl(t.getContext(), url));
				g.addView(v);
			});
			return true;
		} else if (itemId == R.id.settings_fragment) {
			MainActivityDelegate.get(item.getContext()).showFragment(R.id.settings_fragment);
			return true;
		} else if (itemId == R.id.nav_exit) {
			MainActivityDelegate.get(item.getContext()).finish();
			return true;
		}
		MainActivityDelegate a;
		if (BuildConfig.AUTO && (item.getItemId() == R.id.nav_donate)) {
			Context ctx = item.getContext();
			a = MainActivityDelegate.get(ctx);
			a.createDialogBuilder().setTitle(R.drawable.coffee, R.string.donate)
					.setMessage(R.string.donate_text).setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, (d, i) -> openUrl(ctx, DONATE_URL)).show();

			return true;
		}

		return false;
	}

	private static void openUrl(Context ctx, String url) {
		if (!Utils.openUrl(ctx, url)) showInfo(ctx, R.string.use_phone_for_donation);
	}
}
