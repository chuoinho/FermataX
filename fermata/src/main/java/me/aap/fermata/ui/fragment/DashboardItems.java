package me.aap.fermata.ui.fragment;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aap.utils.function.IntSupplier;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonState;
import me.aap.fermata.addon.FermataAddon;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

public final class DashboardItems {
	public static final Pref<Supplier<String[]>> PREF =
			Pref.sa("DASHBOARD_ITEMS", (String[]) null);
	static final Pref<IntSupplier> LAYOUT_VERSION =
			Pref.i("DASHBOARD_LAYOUT_VERSION", 0);
	private static final int UI_REFRESH_LAYOUT_VERSION = 2;
	public static final String DASHBOARD = "dashboard";
	public static final String FOLDERS = "folders";
	public static final String FAVORITES = "favorites";
	public static final String RECENT = "recent";
	public static final String PLAYLISTS = "playlists";
	public static final String MENU = "menu";

	private DashboardItems() {
	}

	@NonNull
	public static List<Item> getDashboardItems(Context ctx) {
		return getDashboardItems(ctx, getPrefs());
	}

	@NonNull
	public static List<Item> getDashboardItems(Context ctx, PreferenceStore store) {
		List<Item> items = new ArrayList<>();
		for (String name : getLayout(store, false, false, false)) {
			Item item = getItem(ctx, name, false);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static List<Item> getConfigItems(Context ctx) {
		return getConfigItems(ctx, getPrefs());
	}

	@NonNull
	public static List<Item> getConfigItems(Context ctx, PreferenceStore store) {
		List<Item> items = new ArrayList<>();
		for (String name : getLayout(store, false, false, true)) {
			Item item = getItem(ctx, name, true);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static Collection<String> getNavLayout(PreferenceStore store) {
		return getLayout(store, true, true, false);
	}

	@NonNull
	public static List<NavItem> getNavItems(PreferenceStore store) {
		List<NavItem> items = new ArrayList<>();
		for (String name : getNavLayout(store)) {
			NavItem item = getNavItem(name);
			if (item != null) items.add(item);
		}
		return items;
	}

	@NonNull
	public static List<String> getConfigLayout(PreferenceStore store) {
		return getLayout(store, false, false, true);
	}

	public static boolean move(PreferenceStore store, String name, int delta) {
		List<String> names = getConfigLayout(store);
		int from = names.indexOf(name);
		if (from == -1) return false;
		int to = from + delta;
		if ((to < 0) || (to >= names.size())) return false;
		names.remove(from);
		names.add(to, name);
		store.applyStringArrayPref(PREF, names.toArray(new String[0]));
		return true;
	}

	public static void setDashboardOrder(PreferenceStore store, List<Item> visibleItems) {
		Set<String> visibleNames = new LinkedHashSet<>(visibleItems.size());
		for (Item item : visibleItems) visibleNames.add(item.name);

		List<String> names = getConfigLayout(store);
		List<String> ordered = new ArrayList<>(names.size());
		Iterator<String> visible = visibleNames.iterator();

		for (String name : names) {
			if (visibleNames.contains(name)) {
				if (visible.hasNext()) ordered.add(visible.next());
			} else {
				ordered.add(name);
			}
		}

		while (visible.hasNext()) ordered.add(visible.next());
		store.applyStringArrayPref(PREF, ordered.toArray(new String[0]));
	}

	public static boolean swap(PreferenceStore store, String name1, String name2) {
		if (!isReorderable(name1) || !isReorderable(name2)) return false;

		List<String> names = getConfigLayout(store);
		int idx1 = names.indexOf(name1);
		int idx2 = names.indexOf(name2);

		if ((idx1 == -1) || (idx2 == -1)) return false;
		names.set(idx1, name2);
		names.set(idx2, name1);
		store.applyStringArrayPref(PREF, names.toArray(new String[0]));
		return true;
	}

	public static void reset(PreferenceStore store) {
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			edit.removePref(PREF);
			edit.removePref(LAYOUT_VERSION);
		}
	}

	@Nullable
	public static String idToName(@IdRes int id) {
		if (id == R.id.dashboard_fragment) return DASHBOARD;
		if (id == R.id.folders_fragment) return FOLDERS;
		if (id == R.id.favorites_fragment) return FAVORITES;
		if (id == R.id.recent_fragment) return RECENT;
		if (id == R.id.playlists_fragment) return PLAYLISTS;
		if (id == R.id.menu) return MENU;

		for (AddonInfo ai : AddonManager.get().getAddonInfos()) {
			if (AddonUiMetadata.isNavigationItem(ai) && (ai.addonId == id)) return ai.className;
		}

		Log.e("Unknown DashboardItem id: ", id);
		return null;
	}

	@Nullable
	private static NavItem getNavItem(String name) {
		switch (name) {
			case DASHBOARD:
				return new NavItem(name, R.id.dashboard_fragment, R.drawable.home, R.string.home);
			case FOLDERS:
				return new NavItem(name, R.id.folders_fragment, me.aap.utils.R.drawable.folder,
						R.string.folders);
			case FAVORITES:
				return new NavItem(name, R.id.favorites_fragment, R.drawable.favorite_filled,
						R.string.favorites);
			case RECENT:
				return new NavItem(name, R.id.recent_fragment, R.drawable.timer, R.string.recent);
			case PLAYLISTS:
				return new NavItem(name, R.id.playlists_fragment, R.drawable.playlist,
						R.string.playlists);
			case MENU:
				return new NavItem(name, R.id.menu, me.aap.utils.R.drawable.menu, R.string.menu);
		}

		AddonInfo info = findAddonInfo(name);
		if (isNavigationAddon(info, false)) {
			return new NavItem(name, info.addonId, info.icon, info.addonName);
		}

		Log.e("Unknown NavBarItem name: ", name);
		return null;
	}

	private static boolean isReorderable(String name) {
		return !DASHBOARD.equals(name) && !MENU.equals(name);
	}

	@NonNull
	private static List<String> getLayout(PreferenceStore store, boolean includeDashboard,
																boolean includeMenu, boolean includeDisabled) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		String[] pref = migrateUiRefreshOrder(store, store.getStringArrayPref(PREF));
		boolean navigationLayout = includeDashboard && includeMenu;

		if (includeDashboard) names.add(DASHBOARD);

		if (pref != null) {
			for (String name : pref) {
				if (isAllowed(name, includeDashboard, includeMenu, includeDisabled,
						navigationLayout)) names.add(name);
			}
		}

		addMissingItem(names, FOLDERS, null);
		addMissingItem(names, FAVORITES, FOLDERS);
		addMissingItem(names, RECENT, FAVORITES);
		addMissingItem(names, PLAYLISTS, RECENT);

		for (AddonInfo ai : AddonManager.get().getAddonInfos()) {
			if ((navigationLayout ? isNavigationAddon(ai, includeDisabled) :
					isFragmentAddon(ai, includeDisabled))) names.add(ai.className);
		}

		if (includeMenu) names.add(MENU);
		return new ArrayList<>(names);
	}

	private static String[] migrateUiRefreshOrder(PreferenceStore store, @Nullable String[] pref) {
		if (store.getIntPref(LAYOUT_VERSION) >= UI_REFRESH_LAYOUT_VERSION) return pref;

		List<String> defaults = new ArrayList<>();
		defaults.add(FOLDERS);
		defaults.add(FAVORITES);
		defaults.add(RECENT);
		defaults.add(PLAYLISTS);
		for (AddonInfo info : AddonManager.get().getAddonInfos()) {
			if (AddonUiMetadata.isDashboardItem(info)) defaults.add(info.className);
		}
		defaults.sort((left, right) -> Integer.compare(uiRefreshPriority(left), uiRefreshPriority(right)));

		String[] migrated = mergeUiRefreshOrder(pref, defaults);
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			edit.setStringArrayPref(PREF, migrated);
			edit.setIntPref(LAYOUT_VERSION, UI_REFRESH_LAYOUT_VERSION);
		}
		return migrated;
	}

	static String[] mergeUiRefreshOrder(@Nullable String[] persisted, Collection<String> defaults) {
		LinkedHashSet<String> merged = new LinkedHashSet<>();
		if (persisted != null) {
			for (String name : persisted) merged.add(name);
		}
		merged.addAll(defaults);
		return merged.toArray(new String[0]);
	}

	private static int uiRefreshPriority(String name) {
		if (DASHBOARD.equals(name)) return -100;
		if (FOLDERS.equals(name)) return 4;
		if (FAVORITES.equals(name)) return 5;
		if (PLAYLISTS.equals(name)) return 6;
		if (RECENT.equals(name)) return 7;
		if (MENU.equals(name)) return 100;
		AddonInfo info = findAddonInfo(name);
		if (info != null) return AddonUiMetadata.priority(info);
		return 8;
	}

	private static void addMissingItem(LinkedHashSet<String> names, String name,
																		 @Nullable String after) {
		if (names.contains(name)) return;
		if ((after == null) || !names.contains(after)) {
			names.add(name);
			return;
		}

		List<String> ordered = new ArrayList<>(names);
		ordered.add(ordered.indexOf(after) + 1, name);
		names.clear();
		names.addAll(ordered);
	}

	private static boolean isAllowed(String name, boolean includeDashboard, boolean includeMenu,
																	boolean includeDisabled, boolean navigationLayout) {
		if (DASHBOARD.equals(name)) return includeDashboard;
		if (MENU.equals(name)) return includeMenu;
		if (FOLDERS.equals(name) || FAVORITES.equals(name) || RECENT.equals(name) ||
				PLAYLISTS.equals(name)) return true;

		for (AddonInfo ai : AddonManager.get().getAddonInfos()) {
			if (ai.className.equals(name)) return navigationLayout ?
					isNavigationAddon(ai, includeDisabled) : isFragmentAddon(ai, includeDisabled);
		}
		return false;
	}

	private static boolean isFragmentAddon(AddonInfo ai, boolean includeDisabled) {
		if (ai == null) return false;
		if (!AddonUiMetadata.isDashboardItem(ai)) return false;
		AddonState state = AddonManager.get().getAddonState(ai);
		return includeDisabled || (state != AddonState.DISABLED);
	}

	private static boolean isNavigationAddon(AddonInfo info, boolean includeDisabled) {
		if ((info == null) || !AddonUiMetadata.isNavigationItem(info)) return false;
		AddonState state = AddonManager.get().getAddonState(info);
		return includeDisabled || (state != AddonState.DISABLED);
	}

	@Nullable
	private static Item getItem(Context ctx, String name, boolean includeDisabled) {
		switch (name) {
			case DASHBOARD:
				return new Item(name, R.id.dashboard_fragment, R.drawable.home,
						ctx.getString(R.string.home), ctx.getString(R.string.dashboard_home_sub), null);
			case FOLDERS:
				return new Item(name, R.id.folders_fragment, me.aap.utils.R.drawable.folder,
						ctx.getString(R.string.folders), ctx.getString(R.string.dashboard_folders_sub), null);
			case FAVORITES:
				return new Item(name, R.id.favorites_fragment, R.drawable.favorite_filled,
						ctx.getString(R.string.favorites), ctx.getString(R.string.dashboard_favorites_sub), null);
			case RECENT:
				return new Item(name, R.id.recent_fragment, R.drawable.timer,
						ctx.getString(R.string.recent), ctx.getString(R.string.dashboard_recent_sub), null);
			case PLAYLISTS:
				return new Item(name, R.id.playlists_fragment, R.drawable.playlist,
						ctx.getString(R.string.playlists), ctx.getString(R.string.dashboard_playlists_sub), null);
			case MENU:
				return new Item(name, R.id.menu, me.aap.utils.R.drawable.menu,
						ctx.getString(R.string.menu), ctx.getString(R.string.dashboard_menu_sub), null);
		}

		AddonInfo info = findAddonInfo(name);
		if (info == null) return null;

		AddonState state = AddonManager.get().getAddonState(info);
		if (isFragmentAddon(info, false)) {
			String subtitle = ((state == AddonState.LOADING) || (state == AddonState.ENABLED_PENDING)) ?
					ctx.getString(R.string.loading) : getAddonSubtitle(ctx, info);
			return new Item(name, info.addonId, info.icon, getAddonTitle(ctx, info),
					subtitle, info);
		}

		if (includeDisabled && isFragmentAddon(info, true)) {
			return new Item(name, ID_NULL, info.icon, getAddonTitle(ctx, info),
					ctx.getString(R.string.dashboard_addon_disabled_sub), info);
		}

		FermataAddon addon = AddonManager.get().getAddon(name);
		if ((addon != null) && includeDisabled) {
			return new Item(name, addon.getAddonId(), info.icon, getAddonTitle(ctx, info),
					getAddonSubtitle(ctx, info), info);
		}

		return null;
	}

	@Nullable
	private static AddonInfo findAddonInfo(String name) {
		return AddonManager.get().getAddonInfo(name);
	}

	private static String getAddonSubtitle(Context ctx, AddonInfo info) {
		return ctx.getString(switch (AddonUiMetadata.role(info)) {
			case TV -> R.string.dashboard_tv_sub;
			case RADIO -> R.string.dashboard_radio_sub;
			case PODCAST -> R.string.dashboard_podcast_sub;
			case AUDIOBOOK -> R.string.dashboard_audiobook_sub;
			case YOUTUBE -> R.string.dashboard_youtube_sub;
			case WEB -> R.string.dashboard_web_sub;
			case FELEX -> R.string.dashboard_felex_sub;
			case GENERIC -> R.string.dashboard_addon_sub;
		});
	}

	private static String getAddonTitle(Context ctx, AddonInfo info) {
		if (AddonUiMetadata.role(info) == AddonUiMetadata.Role.WEB) {
			return ctx.getString(R.string.module_name_web);
		}
		return ctx.getString(info.addonName);
	}

	private static PreferenceStore getPrefs() {
		return FermataApplication.get().getPreferenceStore();
	}

	public static final class Item {
		public final String name;
		@IdRes
		public final int id;
		@DrawableRes
		public final int icon;
		public final String title;
		public final String subtitle;
		@Nullable
		public final AddonInfo addonInfo;

		private Item(String name, @IdRes int id, @DrawableRes int icon, String title,
								 String subtitle, @Nullable AddonInfo addonInfo) {
			this.name = name;
			this.id = id;
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
			this.addonInfo = addonInfo;
		}
	}

	public static final class NavItem {
		public final String name;
		@IdRes
		public final int id;
		@DrawableRes
		public final int icon;
		@StringRes
		public final int title;

		private NavItem(String name, @IdRes int id, @DrawableRes int icon, @StringRes int title) {
			this.name = name;
			this.id = id;
			this.icon = icon;
			this.title = title;
		}
	}
}
