package me.app.fermatax.auto.carapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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

	private LinearLayout rootLayout;
	private FrameLayout viewportContainer;
	private SurfaceView surfaceView;
	private LinearLayout bottomBar;
	private ImageButton btnBack;
	private ImageButton btnDrawer;
	private ImageButton btnPanelPower;
	private ImageButton btnClose;

	private LinearLayout loadingOverlay;
	private LinearLayout needsRootOverlay;
	private LinearLayout serverErrorOverlay;
	private Button btnGrantRoot;

	private final Runnable rootListener = this::updateUiState;
	private final Consumer<CarDisplayClient.State> clientListener = s -> updateUiState();
	private final Consumer<Boolean> panelListener = off -> updateUiState();

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
			client.inject(event);
			return true;
		});
		viewportContainer.addView(surfaceView);

		createStateOverlays(ctx);
		rootLayout.addView(viewportContainer);

		// 2. Bottom bar (3 buttons: Back, App drawer, Close app; height >= 76 dp)
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

	private void createBottomBar(Context ctx) {
		bottomBar = new LinearLayout(ctx);
		bottomBar.setOrientation(LinearLayout.HORIZONTAL);
		bottomBar.setGravity(Gravity.CENTER);

		int barHeight = Math.max(GlassDimensions.dpToPx(ctx, 76), 76);
		LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, barHeight);
		bottomBar.setLayoutParams(barLp);
		bottomBar.setBackground(GlassDrawables.createGlassRailDrawable(ctx, GlassDimensions.PILL_RADIUS_DP, true));

		// 1. Back button
		btnBack = new ImageButton(ctx);
		btnBack.setImageResource(R.drawable.ic_arrow_back);
		btnBack.setContentDescription(ctx.getString(R.string.car_apps_nav_back));
		btnBack.setBackgroundColor(Color.TRANSPARENT);
		btnBack.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		LinearLayout.LayoutParams btnBackLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
		btnBack.setLayoutParams(btnBackLp);
		btnBack.setOnClickListener(v -> client.injectKey(KeyEvent.KEYCODE_BACK));
		bottomBar.addView(btnBack);

		// 2. App drawer button
		btnDrawer = new ImageButton(ctx);
		btnDrawer.setImageResource(R.drawable.view_grid);
		btnDrawer.setContentDescription(ctx.getString(R.string.car_apps_nav_drawer));
		btnDrawer.setBackgroundColor(Color.TRANSPARENT);
		btnDrawer.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		LinearLayout.LayoutParams btnDrawerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
		btnDrawer.setLayoutParams(btnDrawerLp);
		btnDrawer.setOnClickListener(v -> {
			Context c = getContext();
			if (c != null) {
				ComponentName comp = new ComponentName(c, LauncherActivity.class);
				client.launch(comp, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			}
			setScreen(CarAppsNavModel.Screen.DRAWER);
		});
		bottomBar.addView(btnDrawer);

		// 3. Panel power button ("Tắt màn hình điện thoại")
		btnPanelPower = new ImageButton(ctx);
		btnPanelPower.setImageResource(R.drawable.ic_phone_panel_off);
		btnPanelPower.setContentDescription(ctx.getString(R.string.car_apps_panel_off));
		btnPanelPower.setBackgroundColor(Color.TRANSPARENT);
		btnPanelPower.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		LinearLayout.LayoutParams btnPanelPowerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
		btnPanelPower.setLayoutParams(btnPanelPowerLp);
		btnPanelPower.setOnClickListener(v -> {
			Context c = getContext();
			boolean newState = !client.isPanelOff();
			if (c != null) {
				SharedPreferences prefs = c.getSharedPreferences("fermata", Context.MODE_PRIVATE);
				prefs.edit().putBoolean(PanelPowerPolicy.PREF_PANEL_OFF, newState).apply();
			}
			client.setPanelOff(newState);
			updatePanelPowerUi();
		});
		bottomBar.addView(btnPanelPower);

		// 4. Close app button
		btnClose = new ImageButton(ctx);
		btnClose.setImageResource(R.drawable.keyboard_close);
		btnClose.setContentDescription(ctx.getString(R.string.car_apps_nav_close));
		btnClose.setBackgroundColor(Color.TRANSPARENT);
		btnClose.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		LinearLayout.LayoutParams btnCloseLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
		btnClose.setLayoutParams(btnCloseLp);
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
	}

	@Override
	public void onStart() {
		super.onStart();
		CarAppsFeature.addListener(rootListener);
		client.addStateListener(clientListener);
		client.addPanelListener(panelListener);
		if (CarAppsFeature.usable(CarAppsFeature.state())) {
			Context ctx = getContext();
			if (ctx != null) {
				client.ensureRunning(ctx);
			}
		}
		updateUiState();
	}

	@Override
	public void onStop() {
		CarAppsFeature.removeListener(rootListener);
		client.removeStateListener(clientListener);
		client.removePanelListener(panelListener);
		if (client.isPanelOff()) {
			client.setPanelOff(false);
		}
		super.onStop();
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
		if (btnBack != null) {
			btnBack.setEnabled(model.backEnabled);
			btnBack.setAlpha(model.backEnabled ? 1.0f : 0.35f);
		}
		if (btnDrawer != null) {
			btnDrawer.setSelected(model.drawerHighlighted);
			btnDrawer.setAlpha(model.drawerHighlighted ? 1.0f : 0.85f);
		}
		if (btnClose != null) {
			btnClose.setEnabled(model.closeEnabled);
			btnClose.setAlpha(model.closeEnabled ? 1.0f : 0.35f);
		}
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

		if (loadingOverlay == null || needsRootOverlay == null || serverErrorOverlay == null) {
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

		updatePanelPowerUi();

		if (uiState == CarAppsUiState.State.READY && attached) {
			Context c = getContext();
			if (c != null && client.hasCapability(ICarDisplayServer.CAP_PANEL_POWER)) {
				SharedPreferences prefs = c.getSharedPreferences("fermata", Context.MODE_PRIVATE);
				boolean prefOff = prefs.getBoolean(PanelPowerPolicy.PREF_PANEL_OFF, true);
				if (prefOff && !client.isPanelOff()) {
					client.setPanelOff(true);
					updatePanelPowerUi();
				}
			}
		}
	}

	private void updatePanelPowerUi() {
		if (btnPanelPower == null) return;
		boolean capAvailable = client.hasCapability(ICarDisplayServer.CAP_PANEL_POWER);
		if (!capAvailable) {
			btnPanelPower.setVisibility(View.GONE);
			return;
		}
		btnPanelPower.setVisibility(View.VISIBLE);
		boolean off = client.isPanelOff();
		btnPanelPower.setSelected(off);
		btnPanelPower.setAlpha(off ? 1.0f : 0.45f);
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

	public ImageButton getPanelPowerButton() {
		return btnPanelPower;
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
