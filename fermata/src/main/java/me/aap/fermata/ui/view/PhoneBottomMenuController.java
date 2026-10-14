package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.PhoneRootPolicy;

/** Renders the phone-only root selector independently from the addon navigation rail. */
public final class PhoneBottomMenuController {
	private final MainActivityDelegate activity;
	private final BottomNavigationView view;
	private boolean rendering;

	public PhoneBottomMenuController(MainActivityDelegate activity, BottomNavigationView view) {
		this.activity = activity;
		this.view = view;
		applyGlassStyling();
		view.setOnItemSelectedListener(item -> {
			if (rendering) return true;
			return activity.showPhoneRoot(item.getItemId());
		});
	}

	private void applyGlassStyling() {
		if (activity.getPrefs().isGlassTheme(activity.isCarActivity())) {
			android.content.Context ctx = view.getContext();
			boolean isNight = activity.getPrefs().isGlassNightMode();
			view.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 0f, isNight));
			int actionColor = isNight ? 0xFF00E5FF : 0xFF007A87;
			int unselectedColor = isNight ? 0xFF94A3B8 : 0xFF475569;
			android.content.res.ColorStateList iconColors = new android.content.res.ColorStateList(
					new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
					new int[]{actionColor, unselectedColor}
			);
			view.setItemIconTintList(iconColors);
			view.setItemTextColor(iconColors);
		}
	}

	public boolean isGlassTheme() {
		try {
			return activity != null && activity.getPrefs().isGlassTheme(activity.isCarActivity());
		} catch (Throwable e) {
			return false;
		}
	}

	public void refresh() {
		if (isGlassTheme()) {
			view.setVisibility(GONE);
			return;
		}
		applyGlassStyling();
		rendering = true;
		try {
			render(view, activity.getPhoneRootId(), PhoneRootPolicy.showPhoneBottomMenu(
					activity.getRuntimeHostMode(), activity.isBarsHidden()));
		} finally {
			rendering = false;
		}
	}

	public static void render(BottomNavigationView view, int selectedRootId, boolean visible) {
		view.setSelectedItemId(resolveSelectedRoot(selectedRootId));
		view.setVisibility(resolveVisibility(visible));
	}

	static int resolveSelectedRoot(int selectedRootId) {
		return PhoneRootPolicy.normalizePhoneRoot(selectedRootId);
	}

	static int resolveVisibility(boolean visible) {
		return visible ? VISIBLE : GONE;
	}
}
