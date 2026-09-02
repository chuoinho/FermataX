package me.aap.fermata.ui.activity;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.util.Base64.URL_SAFE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.fermata.action.KeyEventHandler.handleKeyEvent;
import static me.aap.fermata.ui.activity.MainActivityPrefs.BRIGHTNESS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CHANGE_BRIGHTNESS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CLOCK_POS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest.permission;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.Settings;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.action.Key;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonState;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.AtvInterface;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExportedItem;
import me.aap.fermata.media.lib.IntentPlayable;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaServiceRuntimeGate;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.MediaSessionCallbackAssistant;
import me.aap.fermata.ui.fragment.AudioEffectsFragment;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.InitialSetupFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.MediaItemNavigationTarget;
import me.aap.fermata.ui.fragment.NavBarMediator;
import me.aap.fermata.ui.fragment.PlaylistsFragment;
import me.aap.fermata.ui.fragment.RecentFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.fragment.SubtitlesFragment;
import me.aap.fermata.ui.policy.BackNavigationPolicy;
import me.aap.fermata.ui.policy.HostRelaunchPolicy;
import me.aap.fermata.ui.policy.ItemRoutePolicy;
import me.aap.fermata.ui.policy.PlaybackLayoutPolicy;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.MediaItemListViewAdapter;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate
		implements MediaSessionCallbackAssistant, PreferenceStore.Listener {
	public static final String INTENT_ACTION_OPEN = "open";
	public static final String INTENT_ACTION_PLAY = "play";
	public static final String INTENT_ACTION_UPDATE = "update";
	public static final String INTENT_ACTION_FINISH = "finish";
	private static final String INTENT_SCHEME = "fermata";
	private final HandlerExecutor handler = new HandlerExecutor(App.get().getHandler().getLooper());
	private final NavBarMediator navBarMediator = new NavBarMediator();
	private final FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private BodyLayout body;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private ContentLoadingProgressBar progressBar;
	private FutureSupplier<?> contentLoading;
	private final AsyncOperationController contentOperations =
			new AsyncOperationController(this::onContentOperationChanged);
	private boolean barsHidden;
	private boolean videoMode;
	private int brightness = 255;
	private final VoiceInteractionCoordinator voiceInteraction;
	private final long diagnosticsActivityId = AsyncOperationController.DiagnosticsObserver.nextId();
	private long hostRelaunchGeneration;
	private boolean hostResumed;

	public MainActivityDelegate(AppActivity activity, FermataServiceUiBinder binder) {
		super(activity);
		mediaServiceBinder = binder;
		voiceInteraction = new VoiceInteractionCoordinator(this);
	}

	@NonNull
	public static MainActivityDelegate get(Context ctx) {
		return (MainActivityDelegate) ActivityDelegate.get(ctx);
	}

	@NonNull
	@SuppressWarnings("unchecked")
	public static FutureSupplier<MainActivityDelegate> getActivityDelegate(Context ctx) {
		return (FutureSupplier<MainActivityDelegate>) ActivityDelegate.getActivityDelegate(ctx);
	}

	public static Context attachBaseContext(Context ctx) {
		MainActivityPrefs prefs = MainActivityPrefs.get();
		return createLocaleContext(ctx, prefs.getLocalePref());
	}

	@NonNull
	@Override
	public Context getLocalizedContext(@NonNull Context ctx) {
		return createLocaleContext(ctx, getPrefs().getLocalePref());
	}

	public static Context createLocaleContext(Context ctx, Locale loc) {
		var cfg = new Configuration(ctx.getResources().getConfiguration());
		setConfigLocale(cfg, loc);
		Locale.setDefault(loc);
		return ctx.createConfigurationContext(cfg);
	}

	@SuppressWarnings("deprecation")
	private static void updateLocaleResources(Context ctx, Locale loc) {
		Resources res = ctx.getResources();
		var cfg = new Configuration(res.getConfiguration());
		setConfigLocale(cfg, loc);
		res.updateConfiguration(cfg, res.getDisplayMetrics());
	}

	private static void setConfigLocale(Configuration cfg, Locale loc) {
		if (VERSION.SDK_INT >= VERSION_CODES.N) cfg.setLocales(new LocaleList(loc));
		else cfg.setLocale(loc);
	}

	public static Uri toIntentUri(String action, String itemId) {
		String id = Base64.encodeToString(itemId.getBytes(US_ASCII), URL_SAFE);
		return new Uri.Builder().scheme(INTENT_SCHEME).authority(action).path(id).build();
	}

	@Nullable
	public static String intentUriToId(Uri u) {
		if ((u == null) || !INTENT_SCHEME.equals(u.getScheme())) return null;
		String id = u.getPath();
		return (id == null) ? null : new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);
	}

	@Nullable
	public static String intentUriToAction(Uri u) {
		return (u != null) && INTENT_SCHEME.equals(u.getScheme()) ? u.getHost() : null;
	}

	@Override
	public void onActivityCreate(@Nullable Bundle state) {
		super.onActivityCreate(state);
		AsyncOperationController.DiagnosticsObserver.activity(
				AsyncOperationController.DiagnosticsObserver.ActivityEvent.CREATED,
				diagnosticsActivityId);
		Intent intent = getIntent();
		if ((intent != null) && INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return;
		}

		getPrefs().addBroadcastListener(this);
		int navId;
		int fragmentId;
		boolean initialSetup = !isCarActivityNotMirror() &&
				getPrefs().shouldShowInitialSetup(this);

		if ((state != null) && state.getBoolean("restoreFragment", false)) {
			navId = state.getInt("navId", ID_NULL);
			fragmentId = state.getInt("fragmentId", ID_NULL);
		} else {
			navId = ID_NULL;
			fragmentId = ID_NULL;
		}
		if ((fragmentId == R.id.initial_setup_fragment) &&
				getPrefs().getBooleanPref(MainActivityPrefs.INITIAL_SETUP_COMPLETED)) {
			// A locale/nav recreation after setup must land on the normal Dashboard.
			navId = ID_NULL;
			fragmentId = ID_NULL;
		}
		final int restoredNavId = navId;
		final int restoredFragmentId = fragmentId;

		AppActivity a = getAppActivity();
		FermataServiceUiBinder b = getMediaServiceBinder();
		Context ctx = a.getContext();
		b.getMediaSessionCallback().getSession().setSessionActivity(
				PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), FLAG_IMMUTABLE));
		b.getMediaSessionCallback().addAssistant(this, isCarActivityNotMirror() ? 0 : 1);
		if (MediaServiceRuntimeGate.allowsAutomaticPrepare(BuildConfig.AUTO)) {
			b.getMediaSessionCallback().prepareIfIdle();
		}
		init();

		AddonManager.get().onActivityCreate(this);

		// Permissions are requested by the feature that needs them. In particular, an Android
		// Auto activity must never try to launch a phone runtime-permission dialog at startup.
		if (restoredFragmentId != ID_NULL) {
			setActiveNavItemId(restoredNavId);
			showFragment(restoredFragmentId);
			return;
		}

		if ((intent != null) && !Intent.ACTION_MAIN.equals(intent.getAction())) {
			handleIntent(intent).onCompletion((r, err) -> {
				if (err != null) Log.e(err, "Failed to handle intent ", intent);
				if ((r == null) || !r) defaultIntent(initialSetup);
			});
		} else {
			defaultIntent(initialSetup);
		}
	}

	@Override
	protected void onActivityNewIntent(Intent intent) {
		if (HostRelaunchPolicy.startsNewNavigation(intent.getAction(), intent.getData() != null))
			hostRelaunchGeneration++;
		super.onActivityNewIntent(intent);
		handleIntent(intent);
	}

	/** Receives relaunch intents from the hosted AA activity implementation. */
	public void onHostNewIntent(Intent intent) {
		onActivityNewIntent(intent);
	}

	public long getHostRelaunchGeneration() {
		return hostRelaunchGeneration;
	}

	public boolean isHostResumed() {
		return hostResumed;
	}

	private FutureSupplier<Boolean> handleIntent(Intent intent) {
		if (INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return completed(true);
		}

		for (FermataAddon a : AddonManager.get().getAddons()) {
			if (a.handleIntent(this, intent)) return completed(true);
		}

		Uri u = intent.getData();

		if (u != null) {
			if (INTENT_SCHEME.equals(u.getScheme())) {
				String action = u.getHost();
				if (action == null) return completed(false);
				String id = u.getPath();
				if (id == null) return completed(false);
				id = new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);

				if (INTENT_ACTION_OPEN.equals(action)) {
					goToItem(id).map(MiscUtils::nonNull);
					return completed(true);
				} else if (INTENT_ACTION_PLAY.equals(action)) {
					goToItem(id).map(i -> {
						if (!(i instanceof PlayableItem)) return false;
						getMediaServiceBinder().playItem((PlayableItem) i);
						return true;
					});
					return completed(true);
				}
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				PlayableItem i = new IntentPlayable(this, u);
				getMediaServiceBinder().stop();
				post(() -> {
					if (!(getActiveFragment() instanceof MediaLibFragment))
						goToCurrent().onSuccess(v -> getMediaServiceBinder().playItem(i));
					else getMediaServiceBinder().playItem(i);
				});
			}
		}

		return completed(false);
	}

	private void defaultIntent(boolean initialSetup) {
		if (getActiveFragment() != null) {
			return;
		}

		if (initialSetup) {
			setActiveNavItemId(ID_NULL);
			showFragment(R.id.initial_setup_fragment);
		} else {
			showDashboard();
		}
	}

	public void showDashboard() {
		hideActiveMenu();
		BodyLayout body = getBody();
		if (!body.isFrameMode()) body.setMode(BodyLayout.Mode.FRAME);
		int previous = getActiveNavItemId();
		setActiveNavItemId(R.id.dashboard_fragment);
		if (showFragment(R.id.dashboard_fragment) == null) setActiveNavItemId(previous);
	}

	@Override
	protected void setUncaughtExceptionHandler() {
		// FermataApplication owns the process-wide handler for mobile, AA, and service paths.
	}

	@Override
	protected void onActivitySaveInstanceState(@NonNull Bundle outState) {
		super.onActivitySaveInstanceState(outState);
		if (isRecreating()) {
			outState.putBoolean("restoreFragment", true);
			outState.putInt("navId", getActiveNavItemId());
			outState.putInt("fragmentId", getActiveFragmentId());
		}
	}

	public void recreate() {
		if (AUTO && isCarActivityNotMirror()) showAlert(getContext(), R.string.please_restart_app);
		else getHandler().post(super::recreate);
	}

	private void refreshLocale() {
		Locale loc = getPrefs().getLocalePref();
		Locale.setDefault(loc);
		updateLocaleResources(App.get(), loc);
		updateLocaleResources(getContext(), loc);

		NavBarView nb = navBar;
		if (nb != null) {
			updateLocaleResources(nb.getContext(), loc);
			navBarMediator.reload(nb);
		}

		ToolBarView tb = toolBar;
		if (tb != null) {
			updateLocaleResources(tb.getContext(), loc);
			ToolBarView.Mediator m = tb.getMediator();
			if (m != null) {
				m.disable(tb);
				m.enable(tb, getActiveFragment());
			}
		}

		if (getActiveFragment() instanceof DashboardFragment dashboard) dashboard.reload();
	}

	@Override
	public void onActivityResume() {
		hostResumed = true;
		getMediaSessionCallback().getHardwareInputRouter().onHostResumed(this);
		super.onActivityResume();
		AsyncOperationController.DiagnosticsObserver.activity(
				AsyncOperationController.DiagnosticsObserver.ActivityEvent.RESUMED,
				diagnosticsActivityId);
		checkMirroringMode(true);
		AddonManager.get().onActivityResume(this);
		onHostForegrounded();
	}

	/** Called when an existing phone or projected host becomes visible again. */
	public void onHostForegrounded() {
		ActivityFragment fragment = getActiveFragment();
		if (fragment instanceof DashboardFragment dashboard) post(dashboard::showHome);
	}

	@Override
	public void onActivityPause() {
		hostResumed = false;
		getMediaSessionCallback().getHardwareInputRouter().onHostPaused(this);
		voiceInteraction.onHostPaused();
		super.onActivityPause();
		AsyncOperationController.DiagnosticsObserver.activity(
				AsyncOperationController.DiagnosticsObserver.ActivityEvent.PAUSED,
				diagnosticsActivityId);
		AddonManager.get().onActivityPause(this);
	}

	@Override
	public void onActivityDestroy() {
		super.onActivityDestroy();
		AsyncOperationController.DiagnosticsObserver.activity(
				AsyncOperationController.DiagnosticsObserver.ActivityEvent.DESTROYED,
				diagnosticsActivityId);
		FutureSupplier<?> loading = contentLoading;
		contentLoading = null;
		if (loading != null) contentOperations.cancel(loading);
		handler.close();
		getMediaServiceBinder().getMediaSessionCallback().removeAssistant(this);
		getPrefs().removeBroadcastListener(this);
		voiceInteraction.close();

		AddonManager.get().onActivityDestroy(this);

		if (me.aap.utils.BuildConfig.D) {
			boolean leaks = ListenerLeakDetector.hasLeaks((b, l) -> {
				if (l instanceof ExportedItem.ListenerWrapper)
					l = ((ExportedItem.ListenerWrapper) l).getListener();
				if (l instanceof Key.PrefsListener) return false;
				if (l instanceof FermataAddon) return false;
				if (l instanceof AtvInterface) return false;
				if ((l instanceof DefaultMediaLib) && (b instanceof DefaultMediaLib)) return false;
				if ((l instanceof MediaEngineManager) && (b instanceof DefaultMediaLib)) return false;
				return (!(l instanceof AddonManager)) ||
						(b != FermataApplication.get().getPreferenceStore());
			});
			if (leaks) Log.e(new IllegalStateException("Listener leaks detected!"));
		}
	}

	public void onActivityFinish() {
		super.onActivityFinish();
	}

	@NonNull
	@Override
	public FermataActivity getAppActivity() {
		return (FermataActivity) super.getAppActivity();
	}

	public boolean isCarActivity() {
		return getRuntimeHostMode().usesAutomotivePresentation();
	}

	public boolean isCarActivityNotMirror() {
		// Preserve the legacy automotive-UI meaning for existing callers. New presentation
		// decisions must use getRuntimeHostMode() when projection and mirror differ.
		return getRuntimeHostMode().usesAutomotivePresentation();
	}

	public RuntimeHostMode getRuntimeHostMode() {
		return RuntimeHostMode.resolve(AUTO, getAppActivity().isCarActivity(),
				FermataApplication.get().isMirroringMode());
	}

	@NonNull
	public MainActivityPrefs getPrefs() {
		return MainActivityPrefs.get();
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return getMediaServiceBinder().getMediaSessionCallback().getPlaybackControlPrefs();
	}

	public static void setTheme(Context ctx, boolean auto) {
		@StyleRes int theme = switch (MainActivityPrefs.get().getThemePref(auto)) {
			case MainActivityPrefs.THEME_LIGHT -> R.style.AppTheme_Light;
			case MainActivityPrefs.THEME_SYSTEM -> {
				if ((ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
						Configuration.UI_MODE_NIGHT_YES)
					yield R.style.AppTheme_Dark;
				else yield R.style.AppTheme_Light;
			}
			case MainActivityPrefs.THEME_BLACK -> R.style.AppTheme_Black;
			case MainActivityPrefs.THEME_STAR_WARS -> R.style.AppTheme_BlackStarWars;
			case MainActivityPrefs.THEME_PURPLE -> R.style.AppTheme_Purple;
			case MainActivityPrefs.THEME_CLASSIC -> R.style.AppTheme_Classic;
			default -> R.style.AppTheme_Dark;
		};
		ctx.setTheme(theme);
		if ((VERSION.SDK_INT >= VERSION_CODES.S) && (ctx instanceof Activity a)) {
			a.getSplashScreen().setSplashScreenTheme(theme);
		}
	}

	@Override
	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if (AUTO && (e.getAction() == MotionEvent.ACTION_DOWN)) {
			FermataActivity a = getAppActivity();

			if (a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.interceptTouchEvent(e, view);
	}

	@Override
	public boolean isFullScreen() {
		if (videoMode || getPrefs().getFullscreenPref(this)) {
			if (isCarActivityNotMirror()) {
				FermataServiceUiBinder b = getMediaServiceBinder();
				return !b.getMediaSessionCallback().getPlaybackControlPrefs().getVideoAaShowStatusPref();
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	public boolean isGridView() {
		ActivityFragment f = getActiveFragment();

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGridSupported()) {
			return getPrefs().getGridViewPref(this);
		} else {
			return false;
		}
	}

	@NonNull
	public FermataServiceUiBinder getMediaServiceBinder() {
		return mediaServiceBinder;
	}

	public MediaSessionCallback getMediaSessionCallback() {
		return getMediaServiceBinder().getMediaSessionCallback();
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaServiceBinder().getLib();
	}

	@Nullable
	public PlayableItem getCurrentPlayable() {
		return getMediaServiceBinder().getCurrentItem();
	}

	public ToolBarView getToolBar() {
		return toolBar;
	}

	@Override
	public float getToolBarSize() {
		return getPrefs().getToolBarSizePref(this);
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	@Override
	public float getNavBarSize() {
		return getPrefs().getNavBarSizePref(this);
	}

	public BodyLayout getBody() {
		return body;
	}

	public NavBarMediator getNavBarMediator() {
		return navBarMediator;
	}


	public ControlPanelView getControlPanel() {
		return controlPanel;
	}

	/** Applies modal text-input policy while preserving the playback state behind the modal UI. */
	public void onTextInputVisibilityChanged(boolean inputActive) {
		ControlPanelView panel = controlPanel;
		if (panel != null) panel.onTextInputVisibilityChanged(inputActive);
	}

	public FloatingButton getFloatingButton() {
		return floatingButton;
	}

	@Override
	public float getTextIconSize() {
		return getPrefs().getTextIconSizePref(this);
	}

	public boolean isBarsHidden() {
		return barsHidden;
	}

	public void setBarsHidden(boolean barsHidden) {
		App.get().getHandler().post(() -> setBarsHiddenNow(barsHidden));
	}

	public void setBarsHiddenNow(boolean barsHidden) {
		// setBarsHidden() is posted to the main handler and may outlive a projection
		// activity while Android Auto is tearing its virtual display down.
		if (getAppActivity().isDestroyed() || getAppActivity().isFinishing()) return;

		if (!barsHidden && getRuntimeHostMode().usesAutomotivePresentation() && videoMode &&
				!isCurrentSplitMode()) {
			ControlPanelView cp = getControlPanel();
			if ((cp == null) || !cp.isVideoControlsVisible()) return;
		}

		this.barsHidden = barsHidden;
		int visibility = barsHidden ? GONE : VISIBLE;
		ToolBarView tb = getToolBar();
		if (barsHidden) tb.setVisibility(GONE);
		else tb.refreshMediatorVisibility();
		getNavBar().setVisibility(visibility);
	}

	private boolean isCurrentSplitMode() {
		MediaEngine engine = getMediaServiceBinder().getCurrentEngine();
		return getBody().isBothMode() && (engine != null) && engine.isSplitModeSupported();
	}

	public void setVideoMode(boolean videoMode, @Nullable VideoView v) {
		ControlPanelView cp = getControlPanel();

		if (videoMode == this.videoMode) {
			if (videoMode && (cp != null)) cp.enableVideoMode(v);
			return;
		}

		long operationId = AsyncOperationController.DiagnosticsObserver.nextId();
		try {
			if (videoMode) {
				this.videoMode = true;
				setSystemUiVisibility();
				keepScreenOn(true);
				if (cp != null) cp.enableVideoMode(v);
			} else {
				this.videoMode = false;
				setSystemUiVisibility();
				keepScreenOn(false);
				if (cp != null) cp.disableVideoMode();
			}

			if (!checkMirroringMode(false)) {
				MainActivityPrefs p = getPrefs();

				if (p.getChangeBrightnessPref()) {
					if (videoMode) {
						brightness = getBrightness();
						setBrightness(p.getBrightnessPref());
					} else {
						setBrightness(brightness);
					}
				}
				if (p.getLandscapeVideoPref()) {
					if (videoMode) {
						getAppActivity().setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
					} else {
						getAppActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					}
				}
			}

			fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			try {
				AsyncOperationController.DiagnosticsObserver.videoMode(
						AsyncOperationController.DiagnosticsObserver.VideoModeEvent.CHANGED,
						operationId, videoMode, getRuntimeHostMode().usesAutomotivePresentation(),
						isCurrentSplitMode());
			} catch (Throwable ignored) {
			}
		} catch (RuntimeException | Error failure) {
			AsyncOperationController.DiagnosticsObserver.videoMode(
						AsyncOperationController.DiagnosticsObserver.VideoModeEvent.FAILED,
						operationId, videoMode, false, false);
			throw failure;
		}
	}

	private boolean checkMirroringMode(boolean clearFlags) {
		if (!AUTO) return false;
		var screenOnFlags =
				FLAG_KEEP_SCREEN_ON | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED;
		var app = FermataApplication.get();
		if (!app.isMirroringMode()) {
			if (clearFlags) getWindow().clearFlags(screenOnFlags);
			return false;
		}
		setFullScreen(true);
		getWindow().addFlags(screenOnFlags);
		getAppActivity().setRequestedOrientation(
				app.isMirroringLandscape() ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
						SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		return true;
	}

	public void keepScreenOn(boolean on) {
		if (on) getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
		else getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
	}

	public int getBrightness() {
		return Settings.System.getInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, 255);
	}

	public void setBrightness(int br) {
		try {
			Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, br);
		} catch (SecurityException ex) {
			Log.e(ex, "Failed to change brightness");
		}
	}

	public boolean isVideoMode() {
		return videoMode;
	}

	public FutureSupplier<?> setContentLoading(FutureSupplier<?> contentLoading) {
		return setContentLoading(this, AsyncOperationController.OperationType.LEGACY,
				contentLoading);
	}

	public FutureSupplier<?> setContentLoading(Object owner,
			AsyncOperationController.OperationType type, FutureSupplier<?> contentLoading) {
		if (contentLoading.isDone()) {
			if (this.contentLoading != null) contentOperations.cancel(this.contentLoading);
			this.contentLoading = null;
			progressBar.hide();
			return contentLoading;
		}

		FutureSupplier<?> main = contentLoading.main();
		AsyncOperationController.Operation<?> operation = contentOperations.start(owner, type, main);
		FutureSupplier<?> active = operation.future();
		this.contentLoading = contentOperations.isActive(operation.token()) ? active : null;
		active.onCompletion((result, failure) -> {
			if ((failure != null) && !isCancellation(failure)) Log.d(failure);
			if (this.contentLoading == active) this.contentLoading = null;
		});
		return active;
	}

	public void clearContentLoading(FutureSupplier<?> owner) {
		if (contentLoading != owner) return;
		contentLoading = null;
		contentOperations.cancel(owner);
	}

	private void onContentOperationChanged(AsyncOperationController.Snapshot snapshot) {
		if (progressBar == null) return;
		boolean listStateVisible = (snapshot.token() != null) &&
				(snapshot.token().owner() instanceof MediaItemListViewAdapter);
		if ((snapshot.state() == AsyncOperationController.State.RUNNING) && !listStateVisible) {
			progressBar.show();
		}
		else if (contentOperations.getSnapshot().state() !=
				AsyncOperationController.State.RUNNING || listStateVisible) progressBar.hide();
	}

	public void backToNavFragment() {
		int id = getActiveNavItemId();
		showFragment((id == ID_NULL) ? R.id.dashboard_fragment : id);
	}

	@Override
	protected int getFrameContainerId() {
		return R.id.frame_layout;
	}

	@Nullable
	@Override
	public ActivityFragment showFragment(int id, Object input) {
		int fromId = ID_NULL;
		try {
			fromId = getActiveFragmentId();
		} catch (Throwable ignored) {
		}
		long operationId = AsyncOperationController.DiagnosticsObserver.navigationStarted(
				diagnosticsActivityId, fromId, id);
		try {
			voiceInteraction.clearSelection();
			BodyLayout b = getBody();
			if (b.isVideoMode()) b.setMode(PlaybackLayoutPolicy.getModeAfterLeavingVideo(isCarActivity()));
			ActivityFragment fragment = super.showFragment(id, input);
			if ((id == R.id.dashboard_fragment) && (fragment instanceof DashboardFragment dashboard)) {
				dashboard.showHome();
			}
			AsyncOperationController.DiagnosticsObserver.navigationCompleted(operationId, id);
			return fragment;
		} catch (RuntimeException | Error failure) {
			AsyncOperationController.DiagnosticsObserver.navigationFailed(operationId, id, failure);
			throw failure;
		}
	}

	public boolean showFragmentWhenReady(int id) {
		return showFragmentWhenReady(id, null);
	}

	public boolean showFragmentWhenReady(int id, @Nullable Object input) {
		AddonManager mgr = FermataApplication.get().getAddonManager();
		AddonInfo info = mgr.getAddonInfo(id);
		if ((info == null) || !info.hasFragment) {
			showFragment(id, input);
			return true;
		}

		AddonState state = mgr.getAddonState(info);
		if (state == AddonState.LOADED) {
			showFragment(id, input);
			return true;
		}
		if (state == AddonState.DISABLED) {
			showAlert(getContext(), R.string.dashboard_addon_disabled_sub);
			return false;
		}

		FutureSupplier<FermataAddon> loading = mgr.getOrInstallAddon(info.className).main(getHandler());
		setContentLoading(info.className, AsyncOperationController.OperationType.INSTALL, loading);
		loading.onSuccess(addon -> showFragment(id, input)).onFailure(err -> {
			if (!isCancellation(err)) {
				String msg = err.getLocalizedMessage();
				showAlert(getContext(), (msg != null) ? msg : err.toString());
			}
		});
		return true;
	}

	protected ActivityFragment createFragment(int id) {
		if (id == R.id.dashboard_fragment) {
			return new DashboardFragment();
		} else if (id == R.id.folders_fragment) {
			return new FoldersFragment();
		} else if (id == R.id.favorites_fragment) {
			return new FavoritesFragment();
		} else if (id == R.id.recent_fragment) {
			return new RecentFragment();
		} else if (id == R.id.playlists_fragment) {
			return new PlaylistsFragment();
		} else if (id == R.id.settings_fragment) {
			return new SettingsFragment();
		} else if (id == R.id.initial_setup_fragment) {
			return new InitialSetupFragment();
		} else if (id == R.id.audio_effects_fragment) {
			return new AudioEffectsFragment();
		} else if (id == R.id.subtitles_fragment) {
			return new SubtitlesFragment();
		}
		ActivityFragment f = FermataApplication.get().getAddonManager().createFragment(id);
		return (f != null) ? f : super.createFragment(id);
	}

	@Nullable
	public MediaLibFragment getActiveMediaLibFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MediaLibFragment) ? (MediaLibFragment) f : null;
	}

	@Nullable
	public MainActivityFragment getActiveMainActivityFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MainActivityFragment) ? (MainActivityFragment) f : null;
	}

	@Nullable
	public MediaLibFragment getMediaLibFragment(int id) {
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (!(f instanceof MediaLibFragment m)) continue;
			if (m.getFragmentId() == id) return m;
		}

		return null;
	}

	public boolean hasCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return (pi != null) || (getLib().getPrefs().getLastPlayedItemPref() != null);
	}

	public FutureSupplier<Boolean> goToCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return ((pi == null) || (pi.isExternal())) ?
				getLib().getLastPlayedItem().main().map(this::goToItem) : completed(goToItem(pi));
	}

	public void onPlayerBackPressed() {
		BackNavigationPolicy.handlePlayerBack(this);
	}

	public FutureSupplier<Item> goToItem(String id) {
		return getLib().getItem(id).main(getHandler()).map(i -> goToItem(i) ? i : null);
	}

	public boolean goToItem(Item i) {
		if (i == null) return false;
		int fragmentId = ItemRoutePolicy.getFragmentId(i);

		if (fragmentId == 0) {
			Log.d("Unsupported item: ", i);
			return false;
		}

		showFragment(fragmentId);

		FermataApplication.get().getHandler().post(() -> {
			ActivityFragment f = getActiveFragment();
			if (f instanceof MediaItemNavigationTarget target) target.showMediaItem(i);
		});

		return true;
	}

	@Override
	protected boolean exitOnBackPressed() {
		return !isCarActivityNotMirror();
	}

	@Override
	public void onBackPressed() {
		BackNavigationPolicy.handleActivityBack(this);
	}

	@Override
	public OverlayMenu createMenu(View anchor) {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getContextMenu() {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getToolBarMenu() {
		return findViewById(R.id.tool_menu);
	}

	public void startVoiceAssistant() {
		voiceInteraction.startContextualAssistant();
	}

	public void startGlobalVoiceControl() {
		voiceInteraction.startGlobalVoiceControl();
	}

	@Override
	public boolean handleVoiceSearch(String query) {
		return voiceInteraction.handleVoiceSearch(query);
	}

	/** Starts a selected item through the active body without exposing its concrete view type. */
	public void playItem(PlayableItem item) {
		getBody().playItem(item);
	}

	public void beginVoiceSelection(List<PlayableItem> items) {
		voiceInteraction.beginSelection(items);
	}

	public void beginVoiceSelection(long requestId, List<PlayableItem> items) {
		voiceInteraction.beginSelection(requestId, items);
	}

	public void beginVoiceSelectionOptions(List<me.aap.fermata.ui.voice.VoiceSession.Option> options) {
		voiceInteraction.beginSelectionOptions(options);
	}

	public void beginVoiceSelectionOptions(long requestId,
			List<me.aap.fermata.ui.voice.VoiceSession.Option> options) {
		voiceInteraction.beginSelectionOptions(requestId, options);
	}

	public boolean isCurrentVoiceTransaction(long requestId) {
		return voiceInteraction.isCurrentTransaction(requestId);
	}

	public void completeVoiceTransaction(long requestId) {
		voiceInteraction.completeTransaction(requestId);
	}

	public void clearVoiceSelection() {
		voiceInteraction.clearSelection();
	}

	/** Returns true while an active selection owns the next voice utterance. */
	public boolean resolveVoiceSelection(String phrase) {
		return voiceInteraction.resolveSelection(phrase);
	}

	public FutureSupplier<List<String>> startSpeechRecognizer() {
		return voiceInteraction.startSpeechRecognizer();
	}

	public FutureSupplier<List<String>> startSpeechRecognizer(String locale, boolean textInput) {
		return voiceInteraction.startSpeechRecognizer(locale, textInput);
	}

	boolean isCurrentVoiceRecognitionSession(VoiceRecognitionSession session) {
		return voiceInteraction.isCurrentSession(session);
	}

	void clearVoiceRecognitionSession(VoiceRecognitionSession session) {
		voiceInteraction.clearSession(session);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getPrevPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getPrevPlayable(i) :
				MediaSessionCallbackAssistant.super.getPrevPlayable(i);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getNextPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getNextPlayable(i) :
				MediaSessionCallbackAssistant.super.getNextPlayable(i);
	}

	@Override
	public EditText createEditText(Context ctx) {
		EditText t = getAppActivity().createEditText(ctx);
		if (isCarActivity() && getPrefs().getVoiceControlEnabledPref()) {
			t.setOnLongClickListener(v -> {
				voiceInteraction.beginTextInput();
				startSpeechRecognizer(null, true).onSuccess(q -> {
					if ((q != null) && !q.isEmpty()) t.setText(q.get(0));
				})
						.onCompletion((result, fail) -> voiceInteraction.beginCommand());
				return true;
			});
		}
		return t;
	}

	@Override
	public DialogBuilder createDialogBuilder(Context ctx) {
		return DialogBuilder.create(getContextMenu());
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
			FutureSupplier<List<PlayableItem>> selection) {
		addPlaylistMenu(builder, () -> selection, () -> "");
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
			Supplier<FutureSupplier<List<PlayableItem>>> selection,
			Supplier<? extends CharSequence> initName) {
		builder.addItem(R.id.playlist_add, R.drawable.playlist_add, R.string.playlist_add)
				.setSubmenu(b -> createPlaylistMenu(b, selection, initName));
	}

	private void createPlaylistMenu(OverlayMenu.Builder b,
			Supplier<FutureSupplier<List<PlayableItem>>> selection,
			Supplier<? extends CharSequence> initName) {
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			b.addItem(R.id.playlist_create, R.drawable.playlist_add, R.string.playlist_create)
					.setHandler(i -> createPlaylist(selection.get(), initName));

			for (int i = 0; i < playlists.size(); i++) {
				Playlist pl = (Playlist) playlists.get(i);
				String name = pl.getName();
				b.addItem(UiUtils.getArrayItemId(i), R.drawable.playlist, name)
						.setHandler(item -> addToPlaylist(name, selection.get()));
			}
		});
	}

	private boolean createPlaylist(FutureSupplier<List<PlayableItem>> selection,
			Supplier<? extends CharSequence> initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, R.drawable.playlist, initName.get())
				.onSuccess(name -> {
					discardSelection();
					if (name == null) return;

					getLib().getPlaylists().addItem(name)
							.onFailure(err -> showAlert(getContext(), err.getMessage())).then(
									pl -> selection.main().then(items -> pl.addItems(items)
											.onFailure(err -> showAlert(getContext(), err.getMessage())).thenRun(() -> {
												MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
												if (f != null) f.getAdapter().reload();
											})));
				});
		return true;
	}

	private boolean addToPlaylist(String name, FutureSupplier<List<PlayableItem>> selection) {
		discardSelection();
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			for (Item i : getLib().getPlaylists().getUnsortedChildren().getOrThrow()) {
				Playlist pl = (Playlist) i;

				if (name.equals(pl.getName())) {
					selection.main().onSuccess(items -> {
						pl.addItems(items);
						MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
						if (f != null) f.getAdapter().reload();
					});
					break;
				}
			}
		});
		return true;
	}

	public void removeFromPlaylist(Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection).onFailure(err -> showAlert(getContext(), err.getMessage()))
				.thenRun(() -> {
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	private void init() {
		FermataActivity a = getAppActivity();
		a.setContentView(getLayout());
		toolBar = a.findViewById(R.id.tool_bar);
		progressBar = a.findViewById(R.id.content_loading_progress);
		navBar = a.findViewById(R.id.nav_bar);
		body = a.findViewById(R.id.body_layout);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		if (getRuntimeHostMode().usesAutomotivePresentation()) floatingButton.setVisibility(GONE);
		controlPanel.bind(getMediaServiceBinder());

		if (VERSION.SDK_INT >= VERSION_CODES.VANILLA_ICE_CREAM && !a.isCarActivity()) {
			ViewCompat.setOnApplyWindowInsetsListener(toolBar, (v, insets) -> {
				var bars = insets.getInsets(
						WindowInsetsCompat.Type.systemBars()
				);
				a.findViewById(R.id.main_activity).setPadding(bars.left, bars.top, bars.right,
						bars.bottom);
				return WindowInsetsCompat.CONSUMED;
			});
		}
	}

	@LayoutRes
	private int getLayout() {
		MainActivityPrefs prefs = getPrefs();
		return switch (prefs.getNavBarPosPref(this)) {
			case NavBarView.POSITION_LEFT -> R.layout.main_activity_left;
			case NavBarView.POSITION_RIGHT -> R.layout.main_activity_right;
			default -> R.layout.main_activity_left;
		};
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (MainActivityPrefs.hasThemePref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasNavBarPosPref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasTextIconSizePref(this, prefs)) {
			if (floatingButton != null) floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		} else if (MainActivityPrefs.hasNavBarSizePref(this, prefs)) {
			if (navBar != null) navBar.setSize(getPrefs().getNavBarSizePref(this));
		} else if (MainActivityPrefs.hasToolBarSizePref(this, prefs)) {
			if (toolBar != null) toolBar.setSize(getPrefs().getToolBarSizePref(this));
		} else if (MainActivityPrefs.hasFullscreenPref(this, prefs)) {
			setSystemUiVisibility();
		} else if (prefs.contains(CHANGE_BRIGHTNESS)) {
			if (getPrefs().getChangeBrightnessPref()) {
				if (!Settings.System.canWrite(getContext())) {
					Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
					i.setData(Uri.parse("package:" + getContext().getPackageName()));
					startActivity(i);
				}
			}
		} else if (prefs.contains(BRIGHTNESS)) {
			if (isVideoMode()) setBrightness(getPrefs().getBrightnessPref());
		} else if (prefs.contains(VOICE_CONTROl_ENABLED)) {
			if (!getPrefs().getVoiceControlEnabledPref()) {
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
				fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
				return;
			}
			fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			if (SpeechRecognitionSupport.handleCarVoicePreference(this)) return;
			getAppActivity().checkPermissions(permission.RECORD_AUDIO).onCompletion((r, err) -> {
				if ((err == null) && (r[0] == PERMISSION_GRANTED)) return;
				if (err != null) Log.e(err, "Failed to request RECORD_AUDIO permission");
				showAlert(getContext(), R.string.err_no_audio_record_perm);
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
			});
		} else if (prefs.contains(VOICE_CONTROL_SUBST)) {
			voiceInteraction.updateWordSubst();
		} else if (prefs.contains(CLOCK_POS)) {
			getBody().getVideoView().setClockPos(getPrefs().getClockPosPref());
		} else if (prefs.contains(LOCALE)) {
			if (AUTO && isCarActivityNotMirror()) refreshLocale();
			else recreate();
		}
	}

	@Override
	public boolean onKeyDown(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyUp(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyLongPress(int code, KeyEvent event,
			IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	public HandlerExecutor getHandler() {
		return handler;
	}

	public Cancellable post(Runnable task) {
		return getHandler().submit(task);
	}

	public Cancellable postDelayed(Runnable task, long delay) {
		return getHandler().schedule(task, delay);
	}

	public Cancellable interruptPlayback() {
		MediaSessionCallback cb = getMediaSessionCallback();
		if (!cb.isPlaying()) return Cancellable.CANCELED;
		PlaybackStateCompat playbackState = cb.getPlaybackState();
		cb.onPause();
		return () -> {
			PlaybackStateCompat state = cb.getPlaybackState();
			if ((state.getState() == PlaybackStateCompat.STATE_PAUSED) &&
					((state == playbackState) || (state.getPosition() != playbackState.getPosition()))) {
				cb.onPlay();
			}
			return true;
		};
	}

	@Override
	protected FutureSupplier<Void> sendCrashReport(Throwable err) {
		return completedVoid();
	}

}
