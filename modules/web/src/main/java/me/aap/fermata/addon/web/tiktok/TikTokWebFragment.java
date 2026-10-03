package me.aap.fermata.addon.web.tiktok;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarRequest;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.view.TopBarController;
import me.aap.fermata.ui.view.TopBarMediatorSupport;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * TikTok-specific configuration of the hosted WebView shell in FermataX.
 */
@Keep
@SuppressWarnings("unused")
public final class TikTokWebFragment extends WebBrowserFragment {

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.tiktok_browser, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		TikTokFloatingNavView navCapsule = view.findViewById(R.id.tiktokNavCapsule);
		if (navCapsule != null) {
			navCapsule.attach(getTikTokWebView());
			adjustFloatingNavPosition(navCapsule);
		}
	}

	public void adjustFloatingNavPosition(@NonNull TikTokFloatingNavView navCapsule) {
		ViewGroup.LayoutParams rawLp = navCapsule.getLayoutParams();
		if (!(rawLp instanceof FrameLayout.LayoutParams lp)) return;

		boolean isNavRight = false;
		try {
			MainActivityDelegate delegate = MainActivityDelegate.get(requireContext());
			NavBarView navBar = (delegate != null) ? delegate.getNavBar() : null;
			if (navBar != null) {
				isNavRight = navBar.isRight();
			} else if (delegate != null) {
				isNavRight = delegate.getPrefs().getNavBarPosPref(delegate) == NavBarView.POSITION_RIGHT;
			}
		} catch (Exception ignored) {
		}

		float density = getResources().getDisplayMetrics().density;
		int bottomMargin = Math.round(24 * density);
		if (isNavRight) {
			lp.gravity = Gravity.BOTTOM | Gravity.START;
			lp.leftMargin = Math.round(76 * density);
			lp.rightMargin = 0;
		} else {
			lp.gravity = Gravity.BOTTOM | Gravity.END;
			lp.rightMargin = Math.round(16 * density);
			lp.leftMargin = 0;
		}
		lp.bottomMargin = bottomMargin;
		navCapsule.setLayoutParams(lp);
	}

	@Override
	public AutomotiveNavigationController.OpenResult openOnCar(OpenOnCarRequest request,
			java.util.function.BooleanSupplier stillCurrent) {
		TikTokWebView web = getTikTokWebView();
		if (!stillCurrent.getAsBoolean()) return AutomotiveNavigationController.OpenResult.CANCELLED;
		if ((request.kind() != OpenOnCarKind.WEB_URL) ||
				(request.addonId() != me.aap.fermata.R.id.tiktok_fragment) ||
				!(request.payload() instanceof String url) ||
				!TikTokOpenOnCarBridge.acceptsRoute(url))
			return AutomotiveNavigationController.OpenResult.FAILED;
		if ((web == null) || isHidden() || !web.isProjectionDestination())
			return AutomotiveNavigationController.OpenResult.NOT_READY;
		String targetUrl = TikTokOpenOnCarBridge.normalizeRouteForCar(url);
		web.stopLoading();
		web.loadUrl(targetUrl);
		return AutomotiveNavigationController.OpenResult.LOAD_DISPATCHED;
	}

	@Override
	public void onDestroyView() {
		View view = getView();
		if (view != null) {
			TikTokFloatingNavView navCapsule = view.findViewById(R.id.tiktokNavCapsule);
			if (navCapsule != null) {
				navCapsule.detach();
			}
		}
		super.onDestroyView();
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.tiktok_fragment;
	}

	@NonNull
	@Override
	public CharSequence getTitle() {
		return "TikTok";
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return TikTokToolBarMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return TikTokFloatingButtonMediator.instance;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder b) {
		try {
			b.addItem(me.aap.fermata.R.id.refresh,
					ResourcesCompat.getDrawable(getResources(), me.aap.fermata.R.drawable.refresh,
							requireContext().getTheme()),
					getString(me.aap.fermata.R.string.refresh)).setHandler(this);
		} catch (Exception ignored) {
		}
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == me.aap.fermata.R.id.refresh) {
			TikTokWebView web = getTikTokWebView();
			if (web != null) web.reload();
			return true;
		}
		return super.menuItemSelected(item);
	}

	@Override
	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected FermataWebClient createWebClient() {
		return new TikTokWebClient();
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.tiktok.com/search?q=";
	}

	void openSearch(String query) {
		voiceCommand(new VoiceCommand(query, VoiceCommand.ACTION_FIND));
	}

	@Nullable
	@Override
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(TikTokAddon.class);
	}

	@Nullable
	public TikTokWebView getTikTokWebView() {
		FermataWebView web = getWebView();
		return (web instanceof TikTokWebView tikTokWebView) ? tikTokWebView : null;
	}

	private static final class TikTokToolBarMediator implements ToolBarView.Mediator.BackTitle {
		static final TikTokToolBarMediator instance = new TikTokToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			TopBarMediatorSupport.installBackTitle(tb, f, this);
			ImageButton refresh = addButton(tb, me.aap.fermata.R.drawable.refresh, this,
					me.aap.fermata.R.id.refresh);
			int minSize = Math.round(48f * tb.getResources().getDisplayMetrics().density);
			refresh.setMinimumWidth(minSize);
			refresh.setMinimumHeight(minSize);
			refresh.setContentDescription(tb.getContext().getString(
					me.aap.fermata.R.string.refresh));
			updateVisibility(tb, f);
		}

		@Override
		public int getVisibility(ToolBarView tb, ActivityFragment f) {
			try {
				MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
				return a.isBarsHidden() ? View.GONE : View.VISIBLE;
			} catch (Exception ignored) {
				return View.VISIBLE;
			}
		}

		@Override
		public void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			ActivityFragment f = a.getActiveFragment();
			if (f != null) updateVisibility(tb, f);
		}

		@Override
		public void onClick(View v) {
			if (v.getId() == me.aap.fermata.R.id.refresh) {
				ActivityFragment fragment = ActivityDelegate.get(v.getContext()).getActiveFragment();
				if (fragment instanceof TikTokWebFragment tiktok) {
					TikTokWebView web = tiktok.getTikTokWebView();
					if (web != null) web.reload();
				}
				return;
			}
			ActivityDelegate.get(v.getContext()).onBackPressed();
		}

		private void updateVisibility(ToolBarView tb, ActivityFragment f) {
			try {
				MainActivityDelegate a = MainActivityDelegate.get(tb.getContext());
				tb.setVisibility(getVisibility(tb, f));
				TopBarController.refresh(a, f);
			} catch (Exception ignored) {
			}
		}
	}

	private static final class TikTokFloatingButtonMediator implements FloatingButton.Mediator {
		static final TikTokFloatingButtonMediator instance = new TikTokFloatingButtonMediator();

		@Override
		public void enable(FloatingButton fb, ActivityFragment f) {
			fb.setVisibility(View.GONE);
		}

		@Override
		public void onActivityEvent(FloatingButton fb, ActivityDelegate a, long e) {
			fb.setVisibility(View.GONE);
		}
	}
}
