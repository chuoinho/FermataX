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
		view.setOnItemSelectedListener(item -> {
			if (rendering) return true;
			return activity.showPhoneRoot(item.getItemId());
		});
	}

	public void refresh() {
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
