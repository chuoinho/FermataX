package me.aap.fermata.ui.view;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
import static me.aap.utils.ui.UiUtils.getTextAppearanceSize;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DimenRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.policy.ItemRoutePolicy;
import me.aap.fermata.ui.policy.PlaybackPresentationReducer.State;
import me.aap.fermata.ui.policy.PlaybackUiPolicy;
import me.aap.fermata.ui.policy.ToolBarTitlePolicy;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.GestureListener;

/**
 * @author Andrey Pavlenko
 */
public class ControlPanelView extends ConstraintLayout
		implements MainActivityListener, PreferenceStore.Listener, OverlayMenu.SelectionHandler,
		GestureListener, FermataServiceUiBinder.Listener {
	private static final byte MASK_VISIBLE = 1;
	private static final byte MASK_VIDEO_MODE = 2;
	private final GestureDetectorCompat gestureDetector;
	private final ImageView showHideBars;
	private final PlayerFavoriteButtonController favoriteController;
	private final PlaybackTimerController playbackTimerController;
	private final PlaybackPresentationCoordinator presentationCoordinator;
	@DimenRes
	private final int size;
	@StyleRes
	private final int textAppearance;
	private PlaybackControlPrefs prefs;
	private HideTimer hideTimer;
	private byte mask;
	private View gestureSource;
	private long scrollStamp;

	public ControlPanelView(Context context, AttributeSet attrs) {
		super(context, attrs, R.attr.appControlPanelStyle);
		gestureDetector = new GestureDetectorCompat(context, this);
		inflate(context, R.layout.control_panel_view, this);

		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ControlPanelView,
				R.attr.appControlPanelStyle, R.style.AppTheme_ControlPanelStyle);
		size = ta.getLayoutDimension(R.styleable.ControlPanelView_size, 0);
		textAppearance = ta.getResourceId(R.styleable.ControlPanelView_textAppearance, 0);
		setBackgroundColor(ta.getColor(R.styleable.ControlPanelView_android_colorBackground, 0));
		ta.recycle();
		playbackTimerController =
				new PlaybackTimerController(this, textAppearance, this::showTimerMenu);

		MainActivityDelegate a = getActivity();
		presentationCoordinator = new PlaybackPresentationCoordinator(
				new PlaybackPresentationCoordinator.Host() {
					@Override
					public void apply(State state) {
						applyAutoPresentation(state);
					}

					@Override
					public void postDelayed(Runnable task, long delay) {
						getActivity().postDelayed(task, delay);
					}
				});
		if (BuildConfig.AUTO && a.isCarActivity()) setBackgroundResource(R.drawable.aa_control_panel_bg);
		a.addBroadcastListener(this, ACTIVITY_DESTROY | FRAGMENT_CHANGED);
		a.getPrefs().addBroadcastListener(this);

		ViewGroup g = findViewById(R.id.show_hide_bars);
		showHideBars = (ImageView) g.getChildAt(0);
		bindBackControl(g);
		showHideBars.setClickable(false);
		showHideBars.setFocusable(false);
		bindBackControl(findViewById(R.id.seek_time));
		g = findViewById(R.id.control_menu_button);
		g.setOnClickListener(this::showMenu);
		favoriteController = new PlayerFavoriteButtonController(a, findViewById(R.id.control_favorite));
		setShowHideBarsIcon(a);
	}

	private void bindBackControl(View v) {
		v.setClickable(true);
		v.setOnClickListener(this::backOrShowHideBars);
		v.setOnTouchListener(this::backOrShowHideBarsTouch);
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable parentState = super.onSaveInstanceState();
		Bundle b = new Bundle();
		b.putByte("MASK", mask);
		b.putParcelable("PARENT", parentState);
		return b;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable st) {
		if (st instanceof Bundle b) {
			super.onRestoreInstanceState(b.getParcelable("PARENT"));
			mask = b.getByte("MASK");
			if (mask != MASK_VISIBLE) super.setVisibility(GONE);
		}
	}

	public void bind(FermataServiceUiBinder b) {
		applyDriverSideControls(true);
		computeSize();
		prefs = b.getMediaSessionCallback().getPlaybackControlPrefs();
		b.addBroadcastListener(this);
		b.bindControlPanel(this);
		b.bindPrevButton(findViewById(R.id.control_prev));
		b.bindRwButton(findViewById(R.id.control_rw));
		b.bindPlayPauseButton(findViewById(R.id.control_play_pause));
		b.bindFfButton(findViewById(R.id.control_ff));
		b.bindNextButton(findViewById(R.id.control_next));
		b.bindProgressBar(findViewById(R.id.seek_bar));
		b.bindProgressTime(findViewById(R.id.seek_time));
		b.bindProgressTotal(findViewById(R.id.seek_total));
		b.bound();
		favoriteController.refresh();
	}

	void computeSize() {
		MainActivityDelegate a = getActivity();
		setSize(a.getPrefs().getControlPanelSizePref(a));
	}

	void applyDriverSideControls(boolean seekEnabled) {
		MainActivityDelegate a = getActivity();
		if (!a.getNavBar().isRight()) return;

		View back = findViewById(R.id.show_hide_bars);
		View menu = findViewById(R.id.control_menu_button);
		View seek = findViewById(R.id.seek_bar);
		View prev = findViewById(R.id.control_prev);
		View rw = findViewById(R.id.control_rw);
		View play = findViewById(R.id.control_play_pause);
		View ff = findViewById(R.id.control_ff);
		View next = findViewById(R.id.control_next);
		View favorite = findViewById(R.id.control_favorite);

		if (seekEnabled) {
			setHorizontal(menu, PARENT_ID, UNSET, R.id.seek_bar, UNSET);
			setHorizontal(seek, UNSET, R.id.control_menu_button, R.id.show_hide_bars, UNSET);
			setHorizontal(back, UNSET, R.id.seek_bar, UNSET, PARENT_ID);
			return;
		}

		setHorizontal(menu, PARENT_ID, UNSET, R.id.control_prev, UNSET);
		setHorizontal(prev, UNSET, R.id.control_menu_button, R.id.control_rw, UNSET);
		setHorizontal(rw, UNSET, R.id.control_prev, R.id.control_play_pause, UNSET);
		setHorizontal(play, UNSET, R.id.control_rw, R.id.control_ff, UNSET);
		setHorizontal(ff, UNSET, R.id.control_play_pause, R.id.control_next, UNSET);
		setHorizontal(next, UNSET, R.id.control_ff, R.id.control_favorite, UNSET);
		setHorizontal(favorite, UNSET, R.id.control_next, R.id.show_hide_bars, UNSET);
		setHorizontal(back, UNSET, R.id.control_favorite, UNSET, PARENT_ID);
	}

	private void setHorizontal(View v, int startToStart, int startToEnd, int endToStart,
														 int endToEnd) {
		ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
		lp.startToStart = startToStart;
		lp.startToEnd = startToEnd;
		lp.endToStart = endToStart;
		lp.endToEnd = endToEnd;
		lp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
		v.setLayoutParams(lp);
	}

	private void setSize(float scale) {
		TextView seekTime = findViewById(R.id.seek_time);
		TextView seekTotal = findViewById(R.id.seek_total);
		float textSize = getTextAppearanceSize(getContext(), textAppearance) * scale;
		int textPad = seekTime.getPaddingTop() + seekTime.getPaddingBottom();
		int pad = 2 * toIntPx(getContext(), 4) + textPad;
		int iconSize = (int) (textSize + pad);
		int panelSize = (int) (size * scale);
		int buttonSize = (int) (panelSize - textSize - pad);
		ControlPanelSeekView seek = findViewById(R.id.seek_bar);

		if (seek.isEnabled()) {
			setHeight(seek, iconSize);
			setSize(R.id.show_hide_bars_icon, iconSize);
			setSize(R.id.control_menu_button_icon, iconSize);
			seTextAppearance(seekTime, textSize);
			seTextAppearance(seekTotal, textSize);
			setHeight(R.id.control_prev, buttonSize);
			setHeight(R.id.control_rw, buttonSize);
			setHeight(R.id.control_play_pause, buttonSize);
			setHeight(R.id.control_ff, buttonSize);
		} else {
			panelSize = buttonSize;
			setSize(R.id.show_hide_bars_icon, buttonSize);
			setSize(R.id.control_menu_button_icon, buttonSize);
			setHeight(R.id.control_prev, buttonSize);
			setHeight(R.id.control_play_pause, buttonSize);
		}

		setHeight(R.id.control_next, buttonSize);
		setHeight(R.id.control_favorite, buttonSize);
		getLayoutParams().height = panelSize;
	}

	private void seTextAppearance(TextView t, float size) {
		t.setTextAppearance(textAppearance);
		t.setTextSize(COMPLEX_UNIT_PX, size);
	}

	private void setSize(@IdRes int id, int size) {
		View v = findViewById(id);
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		lp.width = lp.height = size;
		v.setLayoutParams(lp);
	}

	private void setHeight(@IdRes int id, int h) {
		View v = findViewById(id);
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		lp.height = h;
		v.setLayoutParams(lp);
	}

	private void setHeight(View v, int h) {
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		lp.height = h;
		v.setLayoutParams(lp);
	}

	public boolean isActive() {
		return mask != 0;
	}

	@Override
	public void setVisibility(int visibility) {
		MainActivityDelegate a = getActivity();

		if (visibility == VISIBLE) {
			mask |= MASK_VISIBLE;
			if ((mask & MASK_VIDEO_MODE) != 0) return;
			if (isAutoUi(a)) {
				presentationCoordinator.leaveVideo(isAudioPanelSupported(a));
				return;
			}

			super.setVisibility(VISIBLE);

			if (a.getPrefs().getHideBarsPref(a)) {
				a.setBarsHidden(true);
				setShowHideBarsIcon(a);
			}
		} else {
			mask &= ~MASK_VISIBLE;
			if (isAutoUi(a) && ((mask & MASK_VIDEO_MODE) == 0)) {
				presentationCoordinator.leaveVideo(false);
				return;
			}
			super.setVisibility(GONE);
			a.getFloatingButton().setVisibility(isAutoUi(a) ? GONE : VISIBLE);

			if (a.isBarsHidden()) {
				a.setBarsHidden(false);
				setShowHideBarsIcon(a);
			}
		}

		playbackTimerController.refresh(a);
	}

	public void enableVideoMode(@Nullable VideoView v) {
		MainActivityDelegate a = getActivity();
		hideTimer = null;
		mask |= MASK_VIDEO_MODE;

		View info = (v != null) ? v.getVideoInfoView() : null;

		if (isAutoUi(a)) {
			if (info != null) info.setVisibility(GONE);
			boolean split = a.getBody().isBothMode() && isSplitModeSupported(a);
			presentationCoordinator.enterVideo(split);
			return;
		}

		View fb = a.getFloatingButton();
		int delay = getStartDelay();
		a.setBarsHidden(!isAutoUi(a) || (delay == 0));
		setShowHideBarsIcon(a);

		if (delay == 0) {
			fb.setVisibility(GONE);
			if (info != null) info.setVisibility(GONE);
			super.setVisibility(GONE);
		} else {
			fb.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
			if (info != null) info.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
			updateAutoVideoTitle(a);
			super.setVisibility(VISIBLE);
			hideTimer = isAutoUi(a) ? new HideTimer(a, delay, false, info) :
					new HideTimer(a, delay, false, info, fb);
			a.postDelayed(hideTimer, delay);
		}

		playbackTimerController.refresh(a);
	}

	private boolean isAudioPanelSupported(MainActivityDelegate a) {
		return PlaybackUiPolicy.shouldShowAudioPlayerBar(a);
	}

	private boolean isSplitModeSupported(MainActivityDelegate a) {
		MediaEngine engine = a.getMediaServiceBinder().getCurrentEngine();
		return (engine != null) && engine.isSplitModeSupported();
	}

	public void disableVideoMode() {
		MainActivityDelegate a = getActivity();
		hideTimer = null;
		mask &= ~MASK_VIDEO_MODE;
		a.getFloatingButton().setVisibility(isAutoUi(a) ? GONE : VISIBLE);

		if (isAutoUi(a)) {
			boolean showAudio = ((mask & MASK_VISIBLE) != 0) && isAudioPanelSupported(a);
			presentationCoordinator.leaveVideo(showAudio);
			return;
		}

		if ((mask & MASK_VISIBLE) == 0) {
			super.setVisibility(GONE);
			a.setBarsHidden(false);
		} else {
			super.setVisibility(VISIBLE);
			a.setBarsHidden(a.getPrefs().getHideBarsPref(a));
		}

		setShowHideBarsIcon(a);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		if (isAutoBackTouch(e)) return true;

		MainActivityDelegate a = getActivity();
		if (isAutoUi(a)) {
			presentationCoordinator.refreshTimeout(getTouchDelay());
		} else if (hideTimer != null) {
			int delay = getTouchDelay();
			hideTimer = new HideTimer(a, delay, false, hideTimer.views);
			a.postDelayed(hideTimer, delay);
		}
		return a.interceptTouchEvent(e, me -> {
			gestureSource = this;
			gestureDetector.onTouchEvent(me);
			return super.onTouchEvent(me);
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (handleAutoBackTouchEvent(e)) return true;
		return super.onTouchEvent(e);
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		getActivity().getMediaServiceBinder().onPrevNextButtonClick(true);
		return true;
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		getActivity().getMediaServiceBinder().onPrevNextButtonClick(false);
		return true;
	}

	@Override
	public boolean onSwipeUp(MotionEvent e1, MotionEvent e2) {
		getActivity().getMediaServiceBinder().onPrevNextFolderClick(false);
		return true;
	}

	@Override
	public boolean onSwipeDown(MotionEvent e1, MotionEvent e2) {
		getActivity().getMediaServiceBinder().onPrevNextFolderClick(true);
		return true;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		boolean horizontal = Math.abs(distanceX) >= Math.abs(distanceY);
		long time = System.currentTimeMillis();
		long diff;

		if (horizontal) {
			diff = time - scrollStamp;
			if (diff < 100) return true;
			scrollStamp = time;
		} else {
			diff = time + scrollStamp;
			if (diff < 100) return true;
			scrollStamp = -time;
		}

		if (diff > 500) return true;

		if (horizontal) {
			FermataServiceUiBinder b = getActivity().getMediaServiceBinder();

			switch (e2.getPointerCount()) {
				case 1 -> b.onRwFfButtonClick(distanceX < 0);
				case 2 -> b.onRwFfButtonLongClick(distanceX < 0);
				default -> b.onPrevNextButtonLongClick(distanceX < 0);
			}

			onVideoSeek();
		} else if (e2.getPointerCount() == 2) {
			if (!getActivity().getPrefs().getChangeBrightnessPref()) return true;
			MainActivityDelegate a = getActivity();
			int br = a.getBrightness();
			br = (distanceY > 0) ? Math.min(255, br + 10) : Math.max(0, br - 10);
			a.setBrightness(br);
		} else {
			MediaEngine eng = getActivity().getMediaServiceBinder().getCurrentEngine();
			return (eng != null) && eng.adjustVolume((distanceY > 0) ? ADJUST_RAISE : ADJUST_LOWER);
		}

		return true;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		if (!(gestureSource instanceof VideoView)) return false;
		getActivity().getMediaServiceBinder().onPlayPauseButtonClick();
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		if (!(gestureSource instanceof VideoView)) return false;
		return onTouch((VideoView) gestureSource);
	}

	public boolean onTouch(@Nullable VideoView video) {
		MainActivityDelegate a = getActivity();
		BodyLayout b = a.getBody();

		if (b.getMode() == BodyLayout.Mode.BOTH) {
			b.setMode(BodyLayout.Mode.VIDEO);
			return true;
		}

		int delay = getTouchDelay();
		if (delay == 0) return false;

		View info = (video != null) ? video.getVideoInfoView() : null;
		if (isAutoUi(a)) {
			if (info != null) info.setVisibility(GONE);
			presentationCoordinator.toggleControls(delay);
			return true;
		}

		View fb = a.getFloatingButton();

		if (getVisibility() == VISIBLE) {
			super.setVisibility(GONE);
			fb.setVisibility(GONE);
			if (isAutoUi(a)) a.setBarsHidden(true);
			if (a.getPrefs().getSysBarsOnVideoTouchPref()) a.setFullScreen(true);
			if (info != null) info.setVisibility(GONE);
		} else {
			super.setVisibility(VISIBLE);
			fb.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
			if (isAutoUi(a)) a.setBarsHidden(false);
			if (a.getPrefs().getSysBarsOnVideoTouchPref()) a.setFullScreen(false);
			if (info != null) info.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
			updateAutoVideoTitle(a);
			clearFocus();
			hideTimer = isAutoUi(a) ? new HideTimer(a, delay, false, info) :
					new HideTimer(a, delay, false, info, fb);
			a.postDelayed(hideTimer, delay);
		}

		playbackTimerController.refresh(a);
		return true;
	}

	public void onVideoViewTouch(VideoView view, MotionEvent e) {
		gestureSource = view;
		gestureDetector.onTouchEvent(e);
	}

	public void onVideoSeek() {
		MainActivityDelegate a = getActivity();
		VideoView vv = a.getMediaServiceBinder().getMediaSessionCallback().getVideoView();

		if (vv == null) {
			if (gestureSource instanceof VideoView) vv = (VideoView) gestureSource;
			else return;
		}

		View info = vv.getVideoInfoView();
		int delay = getSeekDelay();
		if (isAutoUi(a)) {
			if (info != null) info.setVisibility(GONE);
			updateAutoVideoTitle(a);
			clearFocus();
			presentationCoordinator.showSeekControls(delay);
			return;
		}

		View fb = a.getFloatingButton();
		super.setVisibility(VISIBLE);
		fb.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
		if (isAutoUi(a)) a.setBarsHidden(false);
		if (info != null) info.setVisibility(isAutoUi(a) ? GONE : VISIBLE);
		updateAutoVideoTitle(a);
		clearFocus();
		hideTimer = isAutoUi(a) ? new HideTimer(a, delay, true, info) :
				new HideTimer(a, delay, true, info, fb);
		a.postDelayed(hideTimer, delay);
		playbackTimerController.refresh(a);
	}

	public boolean isVideoSeekMode() {
		if (isAutoUi(getActivity())) return presentationCoordinator.getState().seekMode();
		HideTimer t = hideTimer;
		return (t != null) && t.seekMode;
	}

	public boolean isVideoControlsVisible() {
		return ((mask & MASK_VIDEO_MODE) != 0) && (getVisibility() == VISIBLE);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			presentationCoordinator.cancel();
			a.getMediaServiceBinder().removeBroadcastListener(this);
			a.getMediaServiceBinder().unbind();
			a.getPrefs().removeBroadcastListener(this);
		} else if ((e == FRAGMENT_CHANGED) && ((mask & MASK_VIDEO_MODE) == 0)) {
			boolean showAudio = ((mask & MASK_VISIBLE) != 0) && isAudioPanelSupported(a);
			presentationCoordinator.leaveVideo(showAudio);
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		favoriteController.refresh();
	}

	@Override
	public void onPlaybackStateChanged(PlaybackStateCompat state) {
		favoriteController.refresh();
		updateAutoVideoTitle(getActivity());
	}

	@Override
	public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		updateAutoVideoTitle(getActivity());
	}

	@Override
	public void onPlaybackStopped() {
		favoriteController.refresh();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		MainActivityDelegate a = getActivity();

		if (MainActivityPrefs.hasControlPanelSizePref(a, prefs)) {
			setSize(a.getPrefs().getControlPanelSizePref(a));
		} else if ((mask == MASK_VISIBLE) && MainActivityPrefs.hasHideBarsPref(a, prefs)) {
			if (a.getPrefs().getHideBarsPref(a)) a.setBarsHidden(getVisibility() == VISIBLE);
			else if (a.isBarsHidden()) a.setBarsHidden(false);
			setShowHideBarsIcon(a);
		}
	}

	public View focusSearch() {
		View v = findViewById(R.id.seek_bar);
		return isVisible(v) ? v : findViewById(R.id.control_play_pause);
	}

	@Override
	public View focusSearch(View focused, int direction) {
		if (focused == null) return super.focusSearch(null, direction);

		if (direction == FOCUS_UP) {
			if (isLine1(focused)) {
				MainActivityDelegate a = getActivity();
				if (a.isVideoMode()) return a.getBody().getVideoView();
				View v = MediaItemListView.focusSearchLast(getContext(), focused);
				if (v != null) return v;
			} else {
				if (!isVisible(findViewById(R.id.seek_bar))) return findViewById(R.id.control_menu_button);
			}
		}

		return super.focusSearch(focused, direction);
	}

	private boolean isLine1(View v) {
		int id = v.getId();
		return id == R.id.seek_bar || id == R.id.show_hide_bars || id == R.id.control_menu_button;
	}

	private void backOrShowHideBars(View v) {
		MainActivityDelegate a = getActivity();
		if (isAutoUi(a)) {
			performAutoPlayerBack(a);
		} else {
			a.setBarsHidden(!a.isBarsHidden());
			setShowHideBarsIcon(a);
		}
	}

	private boolean backOrShowHideBarsTouch(View v, MotionEvent e) {
		MainActivityDelegate a = getActivity();
		if (!isAutoUi(a)) return false;

		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				v.setPressed(true);
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				v.setPressed(false);
				performAutoPlayerBack(a);
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				v.setPressed(false);
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	private boolean handleAutoBackTouchEvent(MotionEvent e) {
		if (!isAutoBackTouch(e) && (e.getActionMasked() != MotionEvent.ACTION_CANCEL)) return false;

		View back = findViewById(R.id.show_hide_bars);
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				back.setPressed(true);
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				back.setPressed(false);
				performAutoPlayerBack(getActivity());
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				back.setPressed(false);
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	private boolean isAutoBackTouch(MotionEvent e) {
		MainActivityDelegate a = getActivity();
		if (!isAutoUi(a)) return false;

		View back = findViewById(R.id.show_hide_bars);
		if (!isVisible(back)) return false;

		int slop = toIntPx(getContext(), 12);
		int edgeTouch = toIntPx(getContext(), 72);
		int left = Math.max(0, back.getLeft() - slop);
		int right = back.getRight() + slop;
		int top = Math.max(0, back.getTop() - slop);
		int bottom = Math.min(getHeight(), back.getBottom() + slop);

		if (a.getNavBar().isRight()) {
			left = Math.min(left, getWidth() - edgeTouch);
			right = Math.max(right, getWidth());
		} else {
			left = Math.min(left, 0);
			right = Math.max(right, edgeTouch);
		}

		float x = e.getX();
		float y = e.getY();
		return (x >= left) && (x <= right) && (y >= top) && (y <= bottom);
	}

	private void performAutoPlayerBack(MainActivityDelegate a) {
		hideTimer = null;
		presentationCoordinator.showControlsPersistent();
		a.onPlayerBackPressed();
	}

	private void applyAutoPresentation(State state) {
		MainActivityDelegate a = getActivity();
		super.setVisibility(state.controlsVisible() ? VISIBLE : GONE);
		a.getFloatingButton().setVisibility(GONE);
		a.setBarsHidden(state.barsHidden());
		if (!state.barsHidden()) {
			updateAutoVideoTitle(a);
			a.post(() -> updateAutoVideoTitle(a));
		}
		setShowHideBarsIcon(a);
		playbackTimerController.refresh(a);
	}

	public void showMenu() {
		if (isActive()) showMenu(this);
	}

	private void showMenu(View v) {
		MainActivityDelegate a = getActivity();
		MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
		PlayableItem i = (eng == null) ? null : eng.getSource();
		if (i != null) new MenuHandler(getMenu(a), i, eng).show();
	}

	private OverlayMenu getMenu(MainActivityDelegate a) {
		return a.findViewById(R.id.control_menu);
	}

	private void setShowHideBarsIcon(MainActivityDelegate a) {
		a.post(() -> showHideBars.setImageResource(
				isAutoUi(a) ? me.aap.utils.R.drawable.back :
						a.isBarsHidden() ? R.drawable.expand : me.aap.utils.R.drawable.collapse));
	}

	private static boolean isAutoUi(MainActivityDelegate a) {
		return BuildConfig.AUTO || a.isCarActivity();
	}

	private void updateAutoVideoTitle(MainActivityDelegate a) {
		if (!isAutoUi(a)) return;
		TextView title = a.getToolBar().findViewById(me.aap.utils.R.id.tool_bar_title);
		if (title == null) return;
		ActivityFragment fragment = a.getActiveFragment();
		if (fragment == null) return;
		PlaybackSnapshot snapshot = a.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem item = snapshot.getItem();
		int ownerId = (item == null) ? 0 : ItemRoutePolicy.getPlaybackOwnerFragmentId(item);
		title.setText(ToolBarTitlePolicy.resolve(fragment.getFragmentId(), ownerId,
				fragment.getTitle(), snapshot.getDisplayTitle()));
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		return true;
	}

	private void showTimerMenu(MainActivityDelegate activity) {
		getMenu(activity).show(builder -> new TimerMenuHandler(activity).build(builder));
	}

	private final class MenuHandler extends MediaItemMenuHandler {
		private final MediaEngine engine;

		public MenuHandler(OverlayMenu menu, Item item, MediaEngine engine) {
			super(menu, item);
			this.engine = engine;
		}

		@Override
		protected boolean addVideoMenu() {
			return !engine.hasVideoMenu();
		}

		@Override
		protected boolean addAudioMenu() {
			PlayableItem pi = engine.getSource();
			return (pi != null) && pi.isVideo() && ((engine.getAudioStreamInfo().size() > 1) ||
					getActivity().getMediaSessionCallback().getEngineManager().isVlcPlayerSupported());
		}

		@Override
		protected void buildAudioMenu(OverlayMenu.Builder b) {
			if (engine.getAudioStreamInfo().size() > 1) {
				b.addItem(R.id.select_audio_stream, R.string.select_audio_stream)
						.setSubmenu(this::buildAudioStreamMenu);
			}
			super.buildAudioMenu(b);
		}

		private void buildAudioStreamMenu(OverlayMenu.Builder b) {
			MediaEngine eng = getActivity().getMediaSessionCallback().getEngine();
			if (eng == null) return;
			AudioStreamInfo ai = eng.getCurrentAudioStreamInfo();
			List<AudioStreamInfo> streams = eng.getAudioStreamInfo();
			b.setSelectionHandler(this::audioStreamSelected);

			for (int i = 0; i < streams.size(); i++) {
				AudioStreamInfo s = streams.get(i);
				b.addItem(UiUtils.getArrayItemId(i), s.toString()).setData(s).setChecked(s.equals(ai));
			}
		}

		private boolean audioStreamSelected(OverlayMenuItem i) {
			MediaEngine eng = getActivity().getMediaSessionCallback().getEngine();
			if (eng != null) {
				AudioStreamInfo ai = i.getData();
				PlayableItem pi = (PlayableItem) getItem();

				if (ai.equals(eng.getCurrentAudioStreamInfo())) {
					pi.getPrefs().setAudioIdPref(null);
					eng.setCurrentAudioStream(null);
				} else {
					eng.setCurrentAudioStream(ai);
					pi.getPrefs().setAudioIdPref(ai.getId());
				}
			}
			return true;
		}

		@Override
		protected boolean addSubtitlesMenu() {
			return engine.isSubtitlesSupported();
		}

		@Override
		protected void buildSubtitlesMenu(OverlayMenu.Builder b) {
			b.addItem(R.id.select_subtitles, R.string.select_subtitles)
					.setFutureSubmenu(this::buildSubtitleStreamMenu);
			super.buildSubtitlesMenu(b);
		}

		private FutureSupplier<Void> buildSubtitleStreamMenu(OverlayMenu.Builder b) {
			b.setSelectionHandler(this::subtitleStreamSelected);
			return engine.getSubtitleStreamInfo().main().map(streams -> {
				SubtitleStreamInfo si = engine.getCurrentSubtitleStreamInfo();
				for (int i = 0; i < streams.size(); i++) {
					SubtitleStreamInfo s = streams.get(i);
					b.addItem(UiUtils.getArrayItemId(i), s.toString()).setData(s).setChecked(s.equals(si));
				}
				return null;
			});
		}

		private boolean subtitleStreamSelected(OverlayMenuItem i) {
			if (getActivity().getMediaSessionCallback().getEngine() != engine) return true;

			SubtitleStreamInfo si = i.getData();
			PlayableItem pi = (PlayableItem) getItem();

			if (si.equals(engine.getCurrentSubtitleStreamInfo())) {
				pi.getPrefs().setSubIdPref(null);
				engine.setCurrentSubtitleStream(null);
			} else {
				engine.setCurrentSubtitleStream(si);
				pi.getPrefs().setSubIdPref(si.getId());
			}

			return true;
		}

		@Override
		protected void buildPlayableMenu(MainActivityDelegate a, OverlayMenu.Builder b,
																		 PlayableItem pi,
																		 boolean initRepeat) {
			super.buildPlayableMenu(a, b, pi, false);

			BrowsableItemPrefs p = pi.getParent().getPrefs();
			MediaEngine eng = a.getMediaSessionCallback().getEngine();
			if (eng == null) return;

			boolean stream = (pi.isStream());
			eng.contributeToMenu(b);

			if (!stream && !pi.isExternal()) {
				if (pi.isRepeatItemEnabled() || p.getRepeatPref()) {
					b.addItem(R.id.repeat, R.drawable.repeat_filled, R.string.repeat).setSubmenu(s -> {
						buildRepeatMenu(s);
						s.addItem(R.id.repeat_disable_all, R.string.repeat_disable);
					});
				} else {
					b.addItem(R.id.repeat_enable, R.drawable.repeat, R.string.repeat)
							.setSubmenu(this::buildRepeatMenu);
				}

				if (p.getShufflePref()) {
					b.addItem(R.id.shuffle_disable, R.drawable.shuffle_filled, R.string.shuffle_disable);
				} else {
					b.addItem(R.id.shuffle_enable, R.drawable.shuffle, R.string.shuffle);
				}
			}

			if (eng.getAudioEffects() != null) {
				b.addItem(R.id.audio_effects_fragment, R.drawable.equalizer, R.string.audio_effects);
			}

			if (!stream) {
				b.addItem(R.id.speed, R.drawable.speed, R.string.speed)
						.setSubmenu(s -> new SpeedMenuHandler().build(s, getItem()));
			}

			b.addItem(R.id.timer, R.drawable.timer, R.string.timer)
					.setSubmenu(s -> new TimerMenuHandler(a).build(s));
		}

		private void buildRepeatMenu(OverlayMenu.Builder b) {
			b.setSelectionHandler(this);
			b.addItem(R.id.repeat_track, R.string.current_track);
			b.addItem(R.id.repeat_folder, R.string.current_folder);
		}

		@Override
		public boolean menuItemSelected(OverlayMenuItem i) {
			int id = i.getItemId();
			PlayableItem pi;
			MediaEngine eng;

			if (id == R.id.audio_effects_fragment) {
				eng = getActivity().getMediaSessionCallback().getEngine();
				if ((eng != null) && (eng.getAudioEffects() != null))
					getActivity().showFragment(R.id.audio_effects_fragment);
				return true;
			} else if (id == R.id.repeat_track || id == R.id.repeat_folder ||
					id == R.id.repeat_disable_all) {
				pi = (PlayableItem) getItem();
				pi.setRepeatItemEnabled(id == R.id.repeat_track);
				pi.getParent().getPrefs().setRepeatPref(id == R.id.repeat_folder);
				return true;
			} else if (id == R.id.shuffle_enable || id == R.id.shuffle_disable) {
				pi = (PlayableItem) getItem();
				pi.getParent().getPrefs().setShufflePref(id == R.id.shuffle_enable);
				return true;
			}

			return super.menuItemSelected(i);
		}
	}

	private final class SpeedMenuHandler implements OverlayMenu.CloseHandler {
		private PrefStore store;

		void build(OverlayMenu.Builder b, Item item) {
			store = new PrefStore(item);
			PreferenceSet set = new PreferenceSet();

			set.addFloatPref(o -> {
				o.title = R.string.speed;
				o.store = store;
				o.pref = MediaPrefs.SPEED;
				o.scale = 0.1f;
				o.seekMin = 1;
				o.seekMax = 20;
			});
			set.addBooleanPref(o -> {
				o.title = R.string.current_track;
				o.store = store;
				o.pref = store.TRACK;
			});
			set.addBooleanPref(o -> {
				o.title = R.string.current_folder;
				o.store = store;
				o.pref = store.FOLDER;
			});

			set.addToMenu(b, true);
			b.setCloseHandlerHandler(this);
		}

		@Override
		public void menuClosed(OverlayMenu menu) {
			store.apply();
		}

		private class PrefStore extends BasicPreferenceStore {
			final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
			final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);
			private final MediaSessionCallback cb =
					getActivity().getMediaServiceBinder().getMediaSessionCallback();
			private final Item item;

			PrefStore(Item item) {
				this.item = item;
				MediaPrefs prefs = item.getPrefs();
				BrowsableItem p = item.getParent();
				boolean set = false;

				try (PreferenceStore.Edit edit = editPreferenceStore()) {
					if (prefs.hasPref(MediaPrefs.SPEED)) {
						edit.setBooleanPref(TRACK, true);
						edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
						set = true;
					} else {
						edit.setBooleanPref(TRACK, false);
					}

					if (p != null) {
						prefs = p.getPrefs();

						if (prefs.hasPref(MediaPrefs.SPEED)) {
							edit.setBooleanPref(FOLDER, true);

							if (!set) {
								edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
								set = true;
							}
						} else {
							edit.setBooleanPref(FOLDER, false);
						}
					} else {
						edit.setBooleanPref(FOLDER, false);
					}

					if (!set) edit.setFloatPref(MediaPrefs.SPEED,
							cb.getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED));
				}
			}

			void apply() {
				BrowsableItem p = item.getParent();
				boolean set = false;

				if (getBooleanPref(TRACK)) {
					item.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
					set = true;
				} else {
					item.getPrefs().removePref(MediaPrefs.SPEED);
				}

				if (p != null) {
					if (getBooleanPref(FOLDER)) {
						p.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
						set = true;
					} else {
						p.getPrefs().removePref(MediaPrefs.SPEED);
					}
				}

				if (!set) {
					cb.getPlaybackControlPrefs()
							.applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
				}
			}

			@Override
			public void applyFloatPref(boolean removeDefault, Pref<? extends DoubleSupplier> pref,
																 float value) {
				if (value == 0.0f) value = 0.1f;
				super.applyFloatPref(removeDefault, pref, value);
				if (cb.isPlaying()) cb.onSetPlaybackSpeed(value);
			}
		}
	}

	private final class TimerMenuHandler extends BasicPreferenceStore
			implements OverlayMenu.CloseHandler {
		private final Pref<IntSupplier> H = Pref.i("H", 0);
		private final Pref<IntSupplier> M = Pref.i("M", 0);
		private final MainActivityDelegate activity;
		private boolean changed;
		private boolean closed;

		TimerMenuHandler(MainActivityDelegate activity) {
			this.activity = activity;
		}

		void build(OverlayMenu.Builder b) {
			PreferenceSet set = new PreferenceSet();
			int time = activity.getMediaSessionCallback().getPlaybackTimer();

			if (time > 0) {
				int h = time / 3600;
				int m = (time - h * 3600) / 60;
				applyIntPref(H, h);
				applyIntPref(M, m);
			}

			set.addIntPref(o -> {
				o.title = R.string.hours;
				o.store = this;
				o.pref = H;
				o.seekMin = 0;
				o.seekMax = 12;
			});
			set.addIntPref(o -> {
				o.title = R.string.minutes;
				o.store = this;
				o.pref = M;
				o.seekMin = 0;
				o.seekMax = 60;
				o.seekScale = 5;
			});

			set.addToMenu(b, true);
			b.setCloseHandlerHandler(this);
			changed = false;
			startTimer();
		}

		@Override
		public void applyIntPref(boolean removeDefault, Pref<? extends IntSupplier> pref, int value) {
			super.applyIntPref(removeDefault, pref, value);
			changed = true;
			startTimer();
		}

		@Override
		public void menuClosed(OverlayMenu menu) {
			closed = true;
			if (!changed) return;
			int h = getIntPref(H);
			int m = getIntPref(M);
			activity.getMediaSessionCallback().setPlaybackTimer(h * 3600 + m * 60);
			playbackTimerController.refresh(activity);
		}

		private void startTimer() {
			activity.postDelayed(() -> {
				if (!closed) getMenu(getActivity()).hide();
			}, 60000);
		}
	}

	private int getStartDelay() {
		return (prefs == null) ? 0 : prefs.getVideoControlStartDelayPref() * 1000;
	}

	private int getTouchDelay() {
		int delay = (prefs == null) ? 5000 : prefs.getVideoControlTouchDelayPref() * 1000;
		return (isAutoUi(getActivity()) && (delay == 0)) ? 5000 : delay;
	}

	private int getSeekDelay() {
		return (prefs == null) ? 3000 : prefs.getVideoControlSeekDelayPref() * 1000;
	}

	private final class HideTimer implements Runnable {
		final MainActivityDelegate activity;
		final int delay;
		final boolean seekMode;
		final View[] views;

		HideTimer(MainActivityDelegate activity, int delay, boolean seekMode, View... views) {
			this.activity = activity;
			this.delay = delay;
			this.seekMode = seekMode;
			this.views = views;
		}

		@Override
		public void run() {
			if ((hideTimer != this) || ((mask & MASK_VIDEO_MODE) == 0)) return;
			if (isAutoUi(activity) && activity.getBody().isBothMode() && isSplitModeSupported(activity)) {
				hideTimer = null;
				activity.setBarsHidden(false);
				return;
			}

			if (!isAutoUi(activity) && ControlPanelView.this.hasFocus()) {
				hideTimer = new HideTimer(activity, delay, seekMode, views);
				activity.postDelayed(hideTimer, delay);
				return;
			}

			if (activity.getPrefs().getSysBarsOnVideoTouchPref()) activity.setFullScreen(true);
			ControlPanelView.super.setVisibility(GONE);
			if (isAutoUi(activity)) activity.setBarsHidden(true);

			for (View v : views) {
				if (v != null) v.setVisibility(GONE);
			}
		}
	}
}
