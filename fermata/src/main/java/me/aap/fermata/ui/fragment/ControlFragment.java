package me.aap.fermata.ui.fragment;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonState;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.AutomotiveNavigationController.OpenResult;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.FermataServiceUiBinder.ControlSeekToken;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.control.ControlPresentation;
import me.aap.fermata.ui.control.ControlSeekGesture;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.UiUtils;

/** Phone-only observer and transport surface for the existing media session. */
public final class ControlFragment extends MainActivityFragment
		implements FermataServiceUiBinder.Listener, AutomotiveConnectionState.Listener,
		AddonManager.Listener {
	@Nullable
	private FermataServiceUiBinder binder;
	@Nullable
	private PlaybackSnapshot playback;
	@Nullable
	private PlaybackTimelineSnapshot timeline;
	@Nullable
	private View root;
	@Nullable
	private ControlPresentation presentation;
	private final ControlSeekGesture seekGesture = new ControlSeekGesture();
	@Nullable
	private ControlSeekToken seekToken;
	private boolean addonsDirty = true;
	@NonNull
	private List<String> addonKeys = List.of();
	private long openRequestGeneration;
	@Nullable
	private View pendingAddonOpen;

	@Override
	public int getFragmentId() {
		return R.id.control_fragment;
	}

	@NonNull
	@Override
	public CharSequence getTitle() {
		return getString(R.string.app_name);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.control_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		root = view;
		FermataServiceUiBinder binder = getActivityDelegate().getMediaServiceBinder();
		this.binder = binder;
		binder.addBroadcastListener(this);
		AutomotiveConnectionState.get().addListener(this);
		AddonManager.get().addBroadcastListener(this);
		playback = binder.getPlaybackSnapshot();
		timeline = binder.getPlaybackTimelineSnapshot();
		wireActions(view);
		render();
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshFromBinder();
		addonsDirty = true;
		refreshAddonsIfVisible();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			cancelPendingAddonOpen();
			cancelControlSeek();
		}
		else {
			addonsDirty = true;
			refreshFromBinder();
			refreshAddonsIfVisible();
		}
	}

	@Override
	public void onPause() {
		cancelPendingAddonOpen();
		cancelControlSeek();
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		cancelPendingAddonOpen();
		AddonManager.get().removeBroadcastListener(this);
		AutomotiveConnectionState.get().removeListener(this);
		FermataServiceUiBinder binder = this.binder;
		if (binder != null) binder.removeBroadcastListener(this);
		this.binder = null;
		playback = null;
		timeline = null;
		presentation = null;
		root = null;
		cancelControlSeek();
		addonKeys = List.of();
		addonsDirty = true;
		super.onDestroyView();
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		cancelControlSeek();
		refreshFromBinder();
	}

	@Override
	public void onPlaybackStateChanged(PlaybackStateCompat state) {
		int value = state.getState();
		if ((value == PlaybackStateCompat.STATE_NONE) ||
				(value == PlaybackStateCompat.STATE_STOPPED) ||
				(value == PlaybackStateCompat.STATE_ERROR)) cancelControlSeek();
		refreshFromBinder();
	}

	@Override
	public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		playback = snapshot;
		renderOnMainThread();
	}

	@Override
	public void onPlaybackTimelineChanged(PlaybackTimelineSnapshot snapshot) {
		timeline = snapshot;
		renderOnMainThread();
	}

	@Override
	public void onPlaybackStopped() {
		cancelControlSeek();
		refreshFromBinder();
	}

	@Override
	public void onStateChanged(AutomotiveConnectionState.State state) {
		renderOnMainThread();
	}

	@Override
	public void onAddonChanged(AddonManager manager, AddonInfo info, boolean installed) {
		addonsDirty = true;
		View root = this.root;
		if (root != null) root.post(this::refreshAddonsIfVisible);
	}

	private void wireActions(View view) {
		view.findViewById(R.id.control_previous).setOnClickListener(ignored -> {
			FermataServiceUiBinder binder = this.binder;
			if (binder != null) binder.skipToPrevious();
		});
		view.findViewById(R.id.control_next).setOnClickListener(ignored -> {
			FermataServiceUiBinder binder = this.binder;
			if (binder != null) binder.skipToNext();
		});
		view.findViewById(R.id.control_play_pause).setOnClickListener(ignored -> {
			FermataServiceUiBinder binder = this.binder;
			if (binder == null) return;
			if (binder.isPlaying()) binder.pause();
			else binder.play();
		});
		SeekBar progress = view.findViewById(R.id.control_progress);
		progress.setOnTouchListener((ignored, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelControlSeek();
			return false;
		});
		progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				ControlPresentation presentation = ControlFragment.this.presentation;
				if (!fromUser || (presentation == null) || !presentation.canSeek) return;
				long preview = Math.min(presentation.durationMillis, progress * 1000L);
				seekGesture.preview(preview);
				TextView elapsed = view.findViewById(R.id.control_elapsed);
				elapsed.setText(timeText(preview));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				FermataServiceUiBinder binder = ControlFragment.this.binder;
				seekToken = (binder == null) ? null : binder.captureControlSeekToken();
				if (seekToken != null) seekGesture.begin();
				else seekGesture.cancel();
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				FermataServiceUiBinder binder = ControlFragment.this.binder;
				ControlSeekToken token = seekToken;
				long position = seekGesture.finish((binder != null) && (token != null));
				seekToken = null;
				if ((binder != null) && (position >= 0L)) binder.seekToIfCurrent(token, position);
				refreshFromBinder();
			}
		});
	}

	private void cancelControlSeek() {
		seekGesture.cancel();
		seekToken = null;
	}

	private void cancelPendingAddonOpen() {
		openRequestGeneration++;
		restorePendingAddonOpen();
	}

	private void restorePendingAddonOpen() {
		View pending = pendingAddonOpen;
		pendingAddonOpen = null;
		if (pending != null) pending.setEnabled(true);
	}

	private void refreshFromBinder() {
		FermataServiceUiBinder binder = this.binder;
		if (binder == null) return;
		playback = binder.getPlaybackSnapshot();
		timeline = binder.getPlaybackTimelineSnapshot();
		renderOnMainThread();
	}

	private void renderOnMainThread() {
		View root = this.root;
		if (root != null) root.post(this::render);
	}

	private void render() {
		View root = this.root;
		if (root == null) return;
		ControlPresentation presentation = ControlPresentation.from(playback, timeline,
				AutomotiveConnectionState.get().state());
		if (!presentation.canSeek) cancelControlSeek();
		this.presentation = presentation;
		updateConnection(root, presentation.automotiveState);
		updateArtwork(root, presentation.metadata);

		TextView title = root.findViewById(R.id.control_title);
		TextView subtitle = root.findViewById(R.id.control_subtitle);
		title.setText(presentation.hasMedia ?
				(presentation.title.length() == 0 ? getString(R.string.control_current_content) :
						presentation.title) : getString(R.string.control_nothing_playing));
		subtitle.setText(presentation.hasMedia && (presentation.subtitle.length() > 0) ?
				presentation.subtitle : getString(R.string.control_open_dashboard));

		SeekBar progress = root.findViewById(R.id.control_progress);
		progress.setEnabled(presentation.canSeek);
		progress.setVisibility(presentation.canSeek ? VISIBLE : INVISIBLE);
		if (presentation.canSeek && !seekGesture.isDragging()) {
			progress.setMax(progressSeconds(presentation.durationMillis));
			progress.setProgress(Math.min(progress.getMax(),
					progressSeconds(presentation.positionMillis)));
		}
		if (!seekGesture.isDragging()) ((TextView) root.findViewById(R.id.control_elapsed)).setText(
				presentation.canSeek ? timeText(presentation.positionMillis) : "");
		((TextView) root.findViewById(R.id.control_duration)).setText(
				presentation.canSeek ? timeText(presentation.durationMillis) : "");

		ImageButton previous = root.findViewById(R.id.control_previous);
		previous.setEnabled(presentation.canPrevious);
		ImageButton playPause = root.findViewById(R.id.control_play_pause);
		playPause.setEnabled(presentation.canPlayPause);
		playPause.setImageResource(presentation.playing ? R.drawable.pause : R.drawable.play);
		playPause.setContentDescription(getString(presentation.playing ? R.string.action_pause :
				R.string.action_play));
		ImageButton next = root.findViewById(R.id.control_next);
		next.setEnabled(presentation.canNext);
	}

	private void updateConnection(View root, AutomotiveConnectionState.State state) {
		TextView statusView = root.findViewById(R.id.control_connection_status);
		if (BuildConfig.AUTO && !AutomotiveConnectionState.get().hasConnectionObservation()) {
			statusView.setText(R.string.control_aa_unknown);
			return;
		}
		int status = switch (state) {
			case CONNECTED -> R.string.control_aa_connected;
			case APP_VISIBLE -> R.string.control_aa_visible;
			case DISCONNECTED -> R.string.control_aa_disconnected;
		};
		statusView.setText(status);
	}

	private void refreshAddonsIfVisible() {
		View root = this.root;
		if ((root == null) || !addonsDirty || !isAdded() || isHidden() ||
				!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
		AddonManager manager = AddonManager.get();
		var items = DashboardItems.getControlAddonItems(root.getContext());
		List<String> keys = new ArrayList<>(items.size());
		for (DashboardItems.Item item : items) {
			AddonState state = manager.getAddonState(item.addonInfo);
			keys.add(item.name + '\n' + state + '\n' + item.title);
		}
		addonsDirty = false;
		if (keys.equals(addonKeys)) return;
		addonKeys = List.copyOf(keys);
		LinearLayout container = root.findViewById(R.id.control_addons);
		container.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(root.getContext());
		for (DashboardItems.Item item : items) {
			AddonState state = manager.getAddonState(item.addonInfo);
			View row = inflater.inflate(R.layout.control_addon_row, container, false);
			((ImageView) row.findViewById(R.id.control_addon_icon)).setImageResource(item.icon);
			((TextView) row.findViewById(R.id.control_addon_title)).setText(item.title);
			int statusRes = DashboardItems.getControlAddonStatusRes(state);
			((TextView) row.findViewById(R.id.control_addon_subtitle)).setText(
					(statusRes == 0) ? item.subtitle : getString(statusRes));
			View open = row.findViewById(R.id.control_addon_open);
			open.setEnabled(DashboardItems.isControlAddonOpenEnabled(state));
			open.setOnClickListener(ignored -> {
				AddonState current = manager.getAddonState(item.addonInfo);
				if (!DashboardItems.isControlAddonOpenEnabled(current)) return;
				restorePendingAddonOpen();
				long request = ++openRequestGeneration;
				pendingAddonOpen = open;
				open.setEnabled(false);
				AutomotiveNavigationController.get().open(item.id).onCompletion((result, error) ->
						root.post(() -> {
							if ((this.root != root) || (request != openRequestGeneration) ||
									!isAdded()) return;
							if (pendingAddonOpen == open) {
								pendingAddonOpen = null;
								open.setEnabled(DashboardItems.isControlAddonOpenEnabled(
										manager.getAddonState(item.addonInfo)));
							}
							addonsDirty = true;
							refreshAddonsIfVisible();
							if ((error != null) || (result == OpenResult.FAILED)) {
								UiUtils.showAlert(root.getContext(), R.string.control_open_failed);
							} else if (result == OpenResult.DISABLED) {
								UiUtils.showAlert(root.getContext(), R.string.dashboard_addon_disabled_sub);
							} else if (result == OpenResult.NOT_READY) {
								UiUtils.showAlert(root.getContext(), R.string.control_open_car_first);
							}
						}));
			});
			container.addView(row);
		}
		root.findViewById(R.id.control_no_addons).setVisibility(
				items.isEmpty() ? VISIBLE : GONE);
	}

	private void updateArtwork(View root, @Nullable MediaMetadataCompat metadata) {
		ImageView artwork = root.findViewById(R.id.control_artwork);
		Bitmap bitmap = (metadata == null) ? null :
				metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
		if (bitmap != null) artwork.setImageBitmap(bitmap);
		else artwork.setImageResource(R.drawable.audiotrack);
	}

	static int displaySeconds(long millis) {
		long seconds = Math.max(0L, millis) / 1000L;
		return (int) Math.min(Integer.MAX_VALUE, seconds);
	}

	static int progressSeconds(long millis) {
		long seconds = (Math.max(0L, millis) + 999L) / 1000L;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, seconds));
	}

	@NonNull
	private static CharSequence timeText(long millis) {
		StringBuilder text = new StringBuilder(10);
		TextUtils.timeToString(text, displaySeconds(millis));
		return text;
	}
}
