package me.app.fermatax.auto.carapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import java.util.function.Consumer;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.app.fermatax.auto.CarService;
import me.app.fermatax.auto.LauncherActivity;

/**
 * Android Auto fragment for independent car apps.
 * Hosts a SurfaceView rendering the root VirtualDisplay and a 3-button bottom bar
 * (Back, App drawer, Close app).
 */
public class CarAppsFragment extends MainActivityFragment {

	private final CarDisplayClient client;
	private CarAppsNavModel.Screen screen = CarAppsNavModel.Screen.DRAWER;
	private CarAppsUiState.State uiState = CarAppsUiState.State.LOADING;
	private boolean attached = false;
	private boolean barsHidden = false;
	private static final long TOOLBAR_PEEK_MS = 4000;
	private boolean peeking;
	private final Runnable hideToolbarTask = () -> {
		peeking = false;
		applyToolbarVisibility();
	};
	private TopEdgeSwipeDetector topSwipe;

	private LinearLayout rootLayout;
	private FrameLayout viewportContainer;
	private SurfaceView surfaceView;
	private LinearLayout bottomBar;
	private ImageButton btnBack;
	private ImageButton btnDrawer;
	private ImageButton btnClose;

	private LinearLayout loadingOverlay;
	private LinearLayout needsRootOverlay;
	private LinearLayout serverErrorOverlay;
	private LinearLayout phoneAsleepOverlay;
	private TextView phoneAsleepText;
	private Button btnGrantRoot;

	private boolean dimRequested;
	private boolean touching;
	private long touchDownTime;
	private float lastTouchX;
	private float lastTouchY;
	private final Runnable rootListener = this::onRootChanged;
	private final Consumer<CarDisplayClient.State> clientListener = s -> updateUiState();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final LauncherActivity.VisibilityListener launcherVisibilityListener = visible -> {
		mainHandler.post(() -> {
			setScreen(visible ? CarAppsNavModel.Screen.DRAWER : CarAppsNavModel.Screen.APP);
		});
	};

	private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder holder) {
			Surface surface = holder.getSurface();
			Rect frame = holder.getSurfaceFrame();
			int w = (frame != null && frame.width() > 0) ? frame.width() :
					(surfaceView != null && surfaceView.getWidth() > 0 ? surfaceView.getWidth() : 1280);
			int h = (frame != null && frame.height() > 0) ? frame.height() :
					(surfaceView != null && surfaceView.getHeight() > 0 ? surfaceView.getHeight() : 720);
			int dpi = getDensityDpi();

			if (!attached) {
				int res = client.attach(surface, w, h, dpi);
				if (res >= 0) {
					attached = true;
					onDisplayAttached();
					updateUiState();
				}
			} else {
				client.setSurface(surface, w, h, dpi);
			}
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
			int dpi = getDensityDpi();
			if (!attached) {
				int res = client.attach(holder.getSurface(), width, height, dpi);
				if (res >= 0) {
					attached = true;
					onDisplayAttached();
					updateUiState();
				}
			} else {
				client.setSurface(holder.getSurface(), width, height, dpi);
			}
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
			cancelActiveTouch();
			client.setSurface(null, 0, 0, 0);
		}
	};

	public CarAppsFragment() {
		this(CarDisplayClient.get());
	}

	CarAppsFragment(CarDisplayClient client) {
		this.client = client != null ? client : CarDisplayClient.get();
	}

	@Override
	public int getFragmentId() {
		return R.id.car_apps_fragment;
	}

	@Override
	public CharSequence getTitle() {
		Context ctx = getContext();
		return ctx != null ? ctx.getString(R.string.car_apps_title) : "Car Apps";
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();

		rootLayout = new LinearLayout(ctx);
		rootLayout.setOrientation(LinearLayout.VERTICAL);
		rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		rootLayout.setBackgroundColor(0xFF07090D);

		// 1. Viewport container (holds SurfaceView and state overlays)
		viewportContainer = new FrameLayout(ctx);
		LinearLayout.LayoutParams viewportLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
		viewportContainer.setLayoutParams(viewportLp);

		surfaceView = new SurfaceView(ctx);
		FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		surfaceView.setLayoutParams(surfaceLp);
		surfaceView.getHolder().addCallback(surfaceCallback);
		surfaceView.setOnTouchListener((v, event) -> {
			forwardTouch(event);
			return true;
		});
		viewportContainer.addView(surfaceView);

		createStateOverlays(ctx);
		createTopEdgeZone(ctx);
		createPhoneAsleepOverlay(ctx);
		rootLayout.addView(viewportContainer);

		// 2. Bottom bar (3 icon buttons: Back, App drawer, Close app; compact 52 dp)
		createBottomBar(ctx);
		rootLayout.addView(bottomBar);

		setScreen(CarAppsNavModel.Screen.DRAWER);
		updateUiStateNow();

		return rootLayout;
	}

	private void createStateOverlays(Context ctx) {
		// Loading overlay
		loadingOverlay = new LinearLayout(ctx);
		loadingOverlay.setOrientation(LinearLayout.VERTICAL);
		loadingOverlay.setGravity(Gravity.CENTER);
		loadingOverlay.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		loadingOverlay.setBackgroundColor(0xFF07090D);
		TextView tvLoading = new TextView(ctx);
		tvLoading.setText(R.string.car_apps_loading);
		tvLoading.setTextColor(0xFFE2E8F0);
		tvLoading.setTextSize(18f);
		tvLoading.setGravity(Gravity.CENTER);
		loadingOverlay.addView(tvLoading);
		viewportContainer.addView(loadingOverlay);

		// Needs root overlay
		needsRootOverlay = new LinearLayout(ctx);
		needsRootOverlay.setOrientation(LinearLayout.VERTICAL);
		needsRootOverlay.setGravity(Gravity.CENTER);
		needsRootOverlay.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		needsRootOverlay.setBackgroundColor(0xFF07090D);
		int pad = GlassDimensions.dpToPx(ctx, 16);
		needsRootOverlay.setPadding(pad, pad, pad, pad);

		TextView tvNeedsRoot = new TextView(ctx);
		tvNeedsRoot.setText(R.string.car_apps_needs_root);
		tvNeedsRoot.setTextColor(0xFFE2E8F0);
		tvNeedsRoot.setTextSize(18f);
		tvNeedsRoot.setGravity(Gravity.CENTER);
		needsRootOverlay.addView(tvNeedsRoot);

		btnGrantRoot = new Button(ctx);
		btnGrantRoot.setText(R.string.car_apps_grant_root);
		btnGrantRoot.setTextColor(Color.WHITE);
		btnGrantRoot.setBackground(GlassDrawables.createGlassPillStateDrawable(ctx, GlassDimensions.PILL_RADIUS_DP, true));
		LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		btnLp.topMargin = GlassDimensions.dpToPx(ctx, 16);
		btnGrantRoot.setLayoutParams(btnLp);
		btnGrantRoot.setOnClickListener(v -> {
			CarAppsFeature.Backend b = CarAppsFeature.backend();
			if (b != null) {
				b.requestRoot();
			}
		});
		needsRootOverlay.addView(btnGrantRoot);
		viewportContainer.addView(needsRootOverlay);

		// Server error overlay
		serverErrorOverlay = new LinearLayout(ctx);
		serverErrorOverlay.setOrientation(LinearLayout.VERTICAL);
		serverErrorOverlay.setGravity(Gravity.CENTER);
		serverErrorOverlay.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		serverErrorOverlay.setBackgroundColor(0xFF07090D);
		TextView tvServerError = new TextView(ctx);
		tvServerError.setText(R.string.car_apps_server_error);
		tvServerError.setTextColor(0xFFEF4444);
		tvServerError.setTextSize(18f);
		tvServerError.setGravity(Gravity.CENTER);
		serverErrorOverlay.addView(tvServerError);
		serverErrorOverlay.setOnClickListener(v -> {
			Context c = getContext();
			if (c != null) {
				client.ensureRunning(c);
			}
		});
		viewportContainer.addView(serverErrorOverlay);
	}

	/**
	 * Added last, so it blocks the apps. A sleeping phone has paused them, and a touch means "continue";
	 * a locked one must not let the car see or control them until it is unlocked on the phone.
	 */
	private void createPhoneAsleepOverlay(Context ctx) {
		phoneAsleepOverlay = new LinearLayout(ctx);
		phoneAsleepOverlay.setOrientation(LinearLayout.VERTICAL);
		phoneAsleepOverlay.setGravity(Gravity.CENTER);
		phoneAsleepOverlay.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		phoneAsleepOverlay.setBackgroundColor(0xFF07090D);
		phoneAsleepText = new TextView(ctx);
		phoneAsleepText.setTextColor(0xFFE2E8F0);
		phoneAsleepText.setTextSize(18f);
		phoneAsleepText.setGravity(Gravity.CENTER);
		phoneAsleepOverlay.addView(phoneAsleepText);
		phoneAsleepOverlay.setOnClickListener(v -> {
			if (client.isPhoneAsleep()) client.wakeScreen();
			dimRequested = false; // the driver wants the car, not the phone: cover it again once it is usable
		});
		viewportContainer.addView(phoneAsleepOverlay);
	}

	/** The zone and the surface share the origin, so view coordinates are display coordinates. */
	void forwardTouch(MotionEvent event) {
		int a = event.getActionMasked();
		if (a == MotionEvent.ACTION_DOWN) {
			touching = true;
			touchDownTime = event.getDownTime();
		} else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
			touching = false;
		}
		lastTouchX = event.getX();
		lastTouchY = event.getY();
		MotionEvent copy = MotionEvent.obtain(event); // keeps tool type and batched history
		copy.setSource(InputDevice.SOURCE_TOUCHSCREEN); // source 0 is not routed as a pointer event
		client.inject(copy);
		copy.recycle();
	}

	/** Without it the app on the car display would keep a finger "down" that no longer exists. */
	private void cancelActiveTouch() {
		if (!touching) return;
		long now = SystemClock.uptimeMillis();
		MotionEvent c = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_CANCEL, lastTouchX, lastTouchY, 0);
		forwardTouch(c);
		c.recycle();
	}

	private void createTopEdgeZone(Context ctx) {
		int edge = GlassDimensions.dpToPx(ctx, 24);
		topSwipe = new TopEdgeSwipeDetector(edge, GlassDimensions.dpToPx(ctx, 48));
		View zone = new View(ctx);
		zone.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, edge, Gravity.TOP));
		zone.setOnTouchListener((v, e) -> {
			topSwipe.onTouch(e.getActionMasked(), e.getX(), e.getY());
			if (topSwipe.consumeTriggered()) {
				// the swipe is ours: end the gesture the app already started, hide the rest from it
				e.setAction(MotionEvent.ACTION_CANCEL);
				forwardTouch(e);
				peekToolbar();
			} else if (topSwipe.forwardsToApp()) {
				forwardTouch(e); // a plain tap on the top edge belongs to the app
			}
			return true;
		});
		viewportContainer.addView(zone);
	}

	private static int themeColor(Context ctx, int attr, int fallback) {
		TypedValue tv = new TypedValue();
		return ctx.getTheme().resolveAttribute(attr, tv, true) && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
				&& tv.type <= TypedValue.TYPE_LAST_COLOR_INT ? tv.data : fallback;
	}

	private ImageButton createNavButton(Context ctx, int iconRes, int descRes) {
		ImageButton b = new ImageButton(ctx);
		b.setImageResource(iconRes);
		b.setContentDescription(ctx.getString(descRes));
		TypedValue tv = new TypedValue();
		ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
		b.setBackgroundResource(tv.resourceId);
		b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
		return b;
	}

	private void createBottomBar(Context ctx) {
		bottomBar = new LinearLayout(ctx);
		bottomBar.setOrientation(LinearLayout.HORIZONTAL);
		bottomBar.setGravity(Gravity.CENTER);
		bottomBar.setLayoutParams(new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, GlassDimensions.dpToPx(ctx, 52)));
		// Flat bar tinted by the active theme, so every theme gets the same Android-style strip.
		int bg = themeColor(ctx, android.R.attr.colorBackground, 0xFF07090D);
		bottomBar.setBackground(new ColorDrawable((bg & 0x00FFFFFF) | 0xE6000000));

		btnBack = createNavButton(ctx, R.drawable.ic_car_nav_back, R.string.car_apps_nav_back);
		btnBack.setOnClickListener(v -> client.injectKey(KeyEvent.KEYCODE_BACK));
		bottomBar.addView(btnBack);

		btnDrawer = createNavButton(ctx, R.drawable.ic_car_nav_drawer, R.string.car_apps_nav_drawer);
		btnDrawer.setOnClickListener(v -> {
			Context c = getContext();
			if (c != null) {
				ComponentName comp = new ComponentName(c, LauncherActivity.class);
				client.launch(comp, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			}
			setScreen(CarAppsNavModel.Screen.DRAWER);
		});
		bottomBar.addView(btnDrawer);

		btnClose = createNavButton(ctx, R.drawable.ic_car_nav_close, R.string.car_apps_nav_close);
		btnClose.setOnClickListener(v -> {
			client.closeTopApp(false);
			setScreen(CarAppsNavModel.Screen.DRAWER);
		});
		btnClose.setOnLongClickListener(v -> {
			client.closeTopApp(true);
			setScreen(CarAppsNavModel.Screen.DRAWER);
			return true;
		});
		bottomBar.addView(btnClose);
		applyNavModel(CarAppsNavModel.of(screen));
	}

	/** Header is hidden while the virtual display is shown; it returns on errors and while peeking. */
	private void applyToolbarVisibility() {
		try {
			CarAppsFeature.setFullscreen(uiState == CarAppsUiState.State.READY && !peeking);
			getActivityDelegate().getToolBar().refreshMediatorVisibility();
		} catch (RuntimeException ignored) {
			// no MainActivityDelegate host (unit tests)
		}
	}

	private void peekToolbar() {
		peeking = true;
		applyToolbarVisibility();
		mainHandler.removeCallbacks(hideToolbarTask);
		mainHandler.postDelayed(hideToolbarTask, TOOLBAR_PEEK_MS);
	}

	private void onRootChanged() {
		Context ctx = getContext();
		// root just granted after a failed start (e.g. denied earlier): retry without a manual tap
		if (ctx != null && client.state() == CarDisplayClient.State.FAILED
				&& CarAppsFeature.state() == RootState.GRANTED) {
			client.ensureRunning(ctx);
		}
		updateUiState();
	}

	@Override
	public void onStart() {
		super.onStart();
		LauncherActivity.addVisibilityListener(launcherVisibilityListener);
		CarAppsFeature.addListener(rootListener);
		client.addStateListener(clientListener);
		if (CarAppsFeature.usable(CarAppsFeature.state())) {
			Context ctx = getContext();
			if (ctx != null) {
				client.ensureRunning(ctx);
			}
		}
		if (LauncherActivity.getActiveInstance() != null) {
			setScreen(CarAppsNavModel.Screen.DRAWER);
		}
		updateUiState();
		applyToolbarVisibility();
	}

	@Override
	public void onStop() {
		LauncherActivity.removeVisibilityListener(launcherVisibilityListener);
		CarAppsFeature.removeListener(rootListener);
		client.removeStateListener(clientListener);
		cancelActiveTouch();
		PhoneDimActivity.dismiss();
		dimRequested = false;
		mainHandler.removeCallbacks(hideToolbarTask);
		peeking = false;
		uiState = CarAppsUiState.State.LOADING;
		applyToolbarVisibility();
		super.onStop();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (surfaceView == null) return;
		// a hidden parent alone leaves the surface alive, and with it the keep-awake lock
		surfaceView.setVisibility(hidden ? View.GONE : View.VISIBLE);
		if (hidden) {
			PhoneDimActivity.dismiss();
			dimRequested = false;
		} else {
			updateUiState();
		}
	}

	@Override
	public boolean onBackPressed() {
		if (screen == CarAppsNavModel.Screen.APP) {
			client.injectKey(KeyEvent.KEYCODE_BACK);
			return true;
		}
		return super.onBackPressed();
	}

	public void setScreen(CarAppsNavModel.Screen newScreen) {
		this.screen = newScreen;
		applyNavModel(CarAppsNavModel.of(newScreen));
	}

	public CarAppsNavModel.Screen getScreen() {
		return screen;
	}

	private void applyNavModel(CarAppsNavModel model) {
		if (btnBack == null) return;
		Context ctx = btnBack.getContext();
		int normal = themeColor(ctx, android.R.attr.textColorPrimary, Color.WHITE);
		int accent = themeColor(ctx, androidx.appcompat.R.attr.colorPrimary, normal);
		btnBack.setEnabled(model.backEnabled);
		btnClose.setEnabled(model.closeEnabled);
		btnBack.setAlpha(model.backEnabled ? 1.0f : 0.35f);
		btnClose.setAlpha(model.closeEnabled ? 1.0f : 0.35f);
		btnDrawer.setSelected(model.drawerHighlighted);
		ImageViewCompat.setImageTintList(btnBack, ColorStateList.valueOf(normal));
		ImageViewCompat.setImageTintList(btnClose, ColorStateList.valueOf(normal));
		ImageViewCompat.setImageTintList(btnDrawer, ColorStateList.valueOf(model.drawerHighlighted ? accent : normal));
	}

	public void setBarsHidden(boolean hidden) {
		this.barsHidden = hidden;
		if (bottomBar != null) {
			bottomBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
		}
	}

	public boolean isBarsHidden() {
		return barsHidden;
	}

	public void updateUiState() {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			View root = getView();
			if (root != null) {
				root.post(this::updateUiStateNow);
			} else {
				new Handler(Looper.getMainLooper()).post(this::updateUiStateNow);
			}
		} else {
			updateUiStateNow();
		}
	}

	private void updateUiStateNow() {
		RootState root = CarAppsFeature.state();
		CarDisplayClient.State clientState = client.state();
		if (clientState != CarDisplayClient.State.READY) {
			attached = false;
		}
		uiState = CarAppsUiState.from(root, clientState);
		applyToolbarVisibility();

		if (loadingOverlay == null || needsRootOverlay == null || serverErrorOverlay == null || phoneAsleepOverlay == null) {
			return;
		}

		switch (uiState) {
			case LOADING:
				loadingOverlay.setVisibility(View.VISIBLE);
				needsRootOverlay.setVisibility(View.GONE);
				serverErrorOverlay.setVisibility(View.GONE);
				break;
			case NEEDS_ROOT:
				loadingOverlay.setVisibility(View.GONE);
				needsRootOverlay.setVisibility(View.VISIBLE);
				serverErrorOverlay.setVisibility(View.GONE);
				if (btnGrantRoot != null) {
					boolean carActive = CarService.isActive();
					btnGrantRoot.setVisibility(carActive ? View.GONE : View.VISIBLE);
				}
				break;
			case SERVER_ERROR:
				loadingOverlay.setVisibility(View.GONE);
				needsRootOverlay.setVisibility(View.GONE);
				serverErrorOverlay.setVisibility(View.VISIBLE);
				break;
			case READY:
				loadingOverlay.setVisibility(View.GONE);
				needsRootOverlay.setVisibility(View.GONE);
				serverErrorOverlay.setVisibility(View.GONE);
				if (!attached) {
					checkAttachSurface();
				}
				break;
		}

		boolean ready = uiState == CarAppsUiState.State.READY;
		boolean asleep = client.isPhoneAsleep();
		boolean locked = client.isPhoneLocked();
		phoneAsleepOverlay.setVisibility(ready && (asleep || locked) ? View.VISIBLE : View.GONE);
		phoneAsleepText.setText(asleep ? R.string.car_apps_phone_asleep : R.string.car_apps_phone_locked);
		// INVISIBLE keeps the viewport size, so the virtual display is not resized
		if (bottomBar != null) bottomBar.setVisibility(ready && locked ? View.INVISIBLE : View.VISIBLE);

		if (uiState == CarAppsUiState.State.READY && attached) {
			applyPanelPref();
		}
	}

	/**
	 * Black phone screen once per attach; the user touching it must not bring it back.
	 * The server starts it because Android blocks the car side, which has no visible activity,
	 * and it waits for the screen to be on because it ends itself when stopped.
	 */
	private void applyPanelPref() {
		Context c = getContext();
		if (c == null || dimRequested || isHidden() || client.isPhoneAsleep() || client.isPhoneLocked()
				|| client.hasOwnPowerGroup()) return;
		SharedPreferences prefs = c.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		if (prefs.getBoolean(CarAppsFeature.PREF_PANEL_OFF, true)) {
			dimRequested = true;
			client.launchOnPhone(new ComponentName(c, PhoneDimActivity.class),
					Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
		}
	}

	private void checkAttachSurface() {
		if (surfaceView == null || attached) return;
		SurfaceHolder holder = surfaceView.getHolder();
		if (holder == null) return;
		Surface surface = holder.getSurface();
		if (surface == null || !surface.isValid()) return;
		Rect frame = holder.getSurfaceFrame();
		int w = (frame != null && frame.width() > 0) ? frame.width() :
				(surfaceView.getWidth() > 0 ? surfaceView.getWidth() : 1280);
		int h = (frame != null && frame.height() > 0) ? frame.height() :
				(surfaceView.getHeight() > 0 ? surfaceView.getHeight() : 720);
		int dpi = getDensityDpi();
		int res = client.attach(surface, w, h, dpi);
		if (res >= 0) {
			attached = true;
			onDisplayAttached();
		}
	}

	private void onDisplayAttached() {
		Context c = getContext();
		if (c != null) {
			ComponentName comp = new ComponentName(c, LauncherActivity.class);
			client.launch(comp, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		}
		setScreen(CarAppsNavModel.Screen.DRAWER);
		applyPanelPref();
	}

	private int getDensityDpi() {
		Context ctx = getContext();
		if (ctx != null) {
			return ctx.getResources().getDisplayMetrics().densityDpi;
		}
		return 160;
	}

	// Accessors for testing and external queries

	public SurfaceView getSurfaceView() {
		return surfaceView;
	}

	public View getBottomBar() {
		return bottomBar;
	}

	public ImageButton getBackButton() {
		return btnBack;
	}

	public ImageButton getDrawerButton() {
		return btnDrawer;
	}

	public ImageButton getCloseButton() {
		return btnClose;
	}

	public View getPhoneAsleepOverlay() {
		return phoneAsleepOverlay;
	}

	public Button getGrantRootButton() {
		return btnGrantRoot;
	}

	public SurfaceHolder.Callback getSurfaceHolderCallback() {
		return surfaceCallback;
	}

	public CarAppsUiState.State getUiState() {
		return uiState;
	}
}
