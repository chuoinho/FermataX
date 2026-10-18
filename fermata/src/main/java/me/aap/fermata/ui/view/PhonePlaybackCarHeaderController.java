package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.auto.AutomotiveConnectionState.State;
import me.aap.fermata.auto.OpenOnCarMode;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.FermataServiceUiBinder.ControlSeekToken;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.media.service.PlaybackTimelineSnapshot;
import me.aap.fermata.ui.control.ControlPresentation;
import me.aap.fermata.ui.control.ControlSeekGesture;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.utils.text.TextUtils;

/** Phone-only compact playback and Android Auto status header. */
public final class PhonePlaybackCarHeaderController implements FermataServiceUiBinder.Listener,
		AutomotiveConnectionState.Listener, me.aap.fermata.ui.activity.MainActivityListener {
	private final FermataServiceUiBinder binder;
	private final OpenOnCarMode mode;
	private final View header;
	private final CompoundButton toggle;
	private final OpenOnCarMode.Listener modeListener = (available, enabled, revision) -> renderLater();
	private final ControlSeekGesture seekGesture = new ControlSeekGesture();
	private ControlSeekToken seekToken;
	private ControlPresentation presentation;
	private boolean rendering;

	public PhonePlaybackCarHeaderController(FermataServiceUiBinder binder, OpenOnCarMode mode,
			View header, CompoundButton toggle) {
		this.binder = binder;
		this.mode = mode;
		this.header = header;
		this.toggle = toggle;
		toggle.setOnCheckedChangeListener((button, checked) -> {
			if (!rendering) mode.setEnabled(checked);
		});
		wirePlaybackControls();
		refreshAppearance();
		binder.addBroadcastListener(this);
		AutomotiveConnectionState.get().addListener(this);
		mode.addListener(modeListener);
		me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(header.getContext());
		if (a != null) a.addBroadcastListener(this);
		render();
	}

	public boolean isGlassTheme() {
		try {
			me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(header.getContext());
			return a != null && a.getPrefs().isGlassTheme(a.isCarActivity());
		} catch (Throwable e) {
			return false;
		}
	}

	public void refreshAppearance() {
		if (!isGlassTheme() || header == null) return;
		android.content.Context ctx = header.getContext();
		me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx);
		boolean isNight = (a != null) ? a.getPrefs().isGlassNightMode() : me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(ctx);
		me.aap.fermata.ui.glass.theme.GlassThemeColors colors = me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);

		header.setBackground(me.aap.fermata.ui.glass.theme.GlassDrawables.createGlassCardDrawable(ctx, 16f, isNight));
		int actionColor = isNight ? 0xFF00E5FF : 0xFF007A87;
		int thumbColor = isNight ? 0xFFFFFFFF : 0xFF15181A;
		int bgTint = isNight ? 0x33FFFFFF : 0x20000000;

		SeekBar progress = header.findViewById(R.id.phone_header_progress);
		if (progress != null) {
			progress.setProgressTintList(android.content.res.ColorStateList.valueOf(actionColor));
			progress.setThumbTintList(android.content.res.ColorStateList.valueOf(thumbColor));
			progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(bgTint));
		}
		android.content.res.ColorStateList trackStateList = new android.content.res.ColorStateList(
				new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
				new int[]{isNight ? 0x6600E5FF : 0x66007A87, bgTint}
		);
		if (toggle instanceof androidx.appcompat.widget.SwitchCompat switchCompat) {
			switchCompat.setThumbTintList(android.content.res.ColorStateList.valueOf(actionColor));
			switchCompat.setTrackTintList(trackStateList);
		} else if (toggle instanceof android.widget.Switch sw) {
			sw.setThumbTintList(android.content.res.ColorStateList.valueOf(actionColor));
			sw.setTrackTintList(trackStateList);
		}

		TextView aaStatus = header.findViewById(R.id.phone_header_aa_status);
		if (aaStatus != null) aaStatus.setTextColor(colors.getTextSecondary());

		View openCarGroup = header.findViewById(R.id.phone_header_open_on_car_group);
		if (openCarGroup instanceof android.view.ViewGroup vg) {
			for (int i = 0; i < vg.getChildCount(); i++) {
				View c = vg.getChildAt(i);
				if (c instanceof TextView tv && !(c instanceof CompoundButton)) {
					tv.setTextColor(colors.getTextSecondary());
				}
			}
		}

		TextView title = header.findViewById(R.id.phone_header_title);
		if (title != null) title.setTextColor(colors.getTextPrimary());
		TextView subtitle = header.findViewById(R.id.phone_header_subtitle);
		if (subtitle != null) subtitle.setTextColor(colors.getTextSecondary());
		ImageButton previous = header.findViewById(R.id.phone_header_previous);
		if (previous != null) previous.setColorFilter(colors.getTextPrimary());
		ImageButton playPause = header.findViewById(R.id.phone_header_play_pause);
		if (playPause != null) playPause.setColorFilter(colors.getTextPrimary());
		ImageButton next = header.findViewById(R.id.phone_header_next);
		if (next != null) next.setColorFilter(colors.getTextPrimary());
	}

	public void refresh(RuntimeHostMode hostMode, boolean barsHidden) {
		header.setVisibility(resolveVisibility(hostMode, barsHidden));
		renderLater();
	}

	static int resolveVisibility(RuntimeHostMode hostMode, boolean barsHidden) {
		return (!barsHidden && (hostMode == RuntimeHostMode.PHONE)) ? VISIBLE : GONE;
	}

	public void close() {
		cancelSeek();
		me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(header.getContext());
		if (a != null) a.removeBroadcastListener(this);
		mode.removeListener(modeListener);
		AutomotiveConnectionState.get().removeListener(this);
		binder.removeBroadcastListener(this);
	}

	@Override
	public void onActivityEvent(me.aap.fermata.ui.activity.MainActivityDelegate a, long event) {
		if (event == me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED) {
			renderLater();
			refreshAppearance();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		cancelSeek();
		renderLater();
	}

	@Override
	public void onPlaybackStateChanged(PlaybackStateCompat state) {
		int value = state.getState();
		if ((value == PlaybackStateCompat.STATE_NONE) ||
				(value == PlaybackStateCompat.STATE_STOPPED) ||
				(value == PlaybackStateCompat.STATE_ERROR)) cancelSeek();
		renderLater();
	}

	@Override
	public void onPlaybackMetadataChanged(PlaybackSnapshot snapshot) {
		renderLater();
	}

	@Override
	public void onPlaybackTimelineChanged(PlaybackTimelineSnapshot snapshot) {
		renderLater();
	}

	@Override
	public void onPlaybackStopped() {
		cancelSeek();
		renderLater();
	}

	@Override
	public void onStateChanged(State state) {
		renderLater();
	}

	private void renderLater() {
		header.post(this::render);
	}

	private void render() {
		rendering = true;
		try {
			toggle.setEnabled(mode.isAvailable());
			toggle.setChecked(mode.isEnabled());
		} finally {
			rendering = false;
		}
		ControlPresentation presentation = ControlPresentation.from(binder.getPlaybackSnapshot(),
				binder.getPlaybackTimelineSnapshot(), AutomotiveConnectionState.get().state());
		if (!presentation.canSeek) cancelSeek();
		this.presentation = presentation;

		View summary = header.findViewById(R.id.phone_header_summary);
		SeekBar progress = header.findViewById(R.id.phone_header_progress);
		View times = header.findViewById(R.id.phone_header_times);

		if (!shouldShowSummary(presentation.hasMedia)) {
			summary.setVisibility(GONE);
			progress.setVisibility(GONE);
			times.setVisibility(GONE);
		} else {
			summary.setVisibility(VISIBLE);
			TextView title = header.findViewById(R.id.phone_header_title);
			TextView subtitle = header.findViewById(R.id.phone_header_subtitle);
			title.setText((presentation.title.length() > 0) ?
					presentation.title : header.getContext().getString(R.string.phone_header_nothing_playing));
			subtitle.setText((presentation.subtitle.length() > 0) ?
					presentation.subtitle : header.getContext().getString(R.string.phone_header_choose_media));
			ImageView artwork = header.findViewById(R.id.phone_header_artwork);
			MediaMetadataCompat metadata = presentation.metadata;
			Bitmap bitmap = (metadata == null) ? null :
					metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
			float density = header.getResources().getDisplayMetrics().density;
			if (bitmap == null) {
				int pad = Math.round(8 * density);
				artwork.setPadding(pad, pad, pad, pad);
				artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
				artwork.setImageResource(R.drawable.audiotrack);
				me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(header.getContext());
				int iconTint = (a != null && a.isGlassTheme()) ?
						me.aap.fermata.ui.glass.theme.GlassThemeColors.get(header.getContext()).getIconColor() : 0xFF94A3B8;
				artwork.setColorFilter(iconTint);
			} else {
				int pad = Math.round(4 * density);
				artwork.setPadding(pad, pad, pad, pad);
				artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
				artwork.clearColorFilter();
				artwork.setImageBitmap(bitmap);
			}

			ImageButton previous = header.findViewById(R.id.phone_header_previous);
			ImageButton playPause = header.findViewById(R.id.phone_header_play_pause);
			ImageButton next = header.findViewById(R.id.phone_header_next);
			previous.setEnabled(presentation.canPrevious);
			previous.setAlpha(presentation.canPrevious ? 1f : 0.45f);
			playPause.setEnabled(presentation.canPlayPause);
			playPause.setAlpha(presentation.canPlayPause ? 1f : 0.45f);
			playPause.setImageResource(presentation.playing ? R.drawable.pause : R.drawable.play);
			playPause.setContentDescription(header.getContext().getString(presentation.playing ?
					R.string.action_pause : R.string.action_play));
			next.setEnabled(presentation.canNext);
			next.setAlpha(presentation.canNext ? 1f : 0.45f);

			if (isGlassTheme()) {
				android.content.Context ctx = header.getContext();
				me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx);
				boolean isNight = (a != null) ? a.getPrefs().isGlassNightMode() : me.aap.fermata.ui.glass.theme.GlassThemeColors.isNight(ctx);
				me.aap.fermata.ui.glass.theme.GlassThemeColors gc =
						me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);
				title.setTextColor(gc.getTextPrimary());
				subtitle.setTextColor(gc.getTextSecondary());
				previous.setColorFilter(gc.getTextPrimary());
				playPause.setColorFilter(gc.getTextPrimary());
				next.setColorFilter(gc.getTextPrimary());
				TextView pos = header.findViewById(R.id.phone_header_elapsed);
				if (pos != null) pos.setTextColor(gc.getTextSecondary());
				TextView dur = header.findViewById(R.id.phone_header_duration);
				if (dur != null) dur.setTextColor(gc.getTextSecondary());
			}

			updateTimeline(presentation);
		}

		State state = presentation.automotiveState;
		((TextView) header.findViewById(R.id.phone_header_aa_status)).setText(aaStatus(state));
		header.findViewById(R.id.phone_header_aa_dot).setSelected(state != State.DISCONNECTED);
	}

	private void wirePlaybackControls() {
		header.findViewById(R.id.phone_header_previous).setOnClickListener(v -> binder.skipToPrevious());
		header.findViewById(R.id.phone_header_next).setOnClickListener(v -> binder.skipToNext());
		header.findViewById(R.id.phone_header_play_pause).setOnClickListener(v -> {
			if (binder.isPlaying()) binder.pause();
			else binder.play();
		});
		SeekBar progress = header.findViewById(R.id.phone_header_progress);
		progress.setOnTouchListener((ignored, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelSeek();
			return false;
		});
		progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				ControlPresentation presentation = PhonePlaybackCarHeaderController.this.presentation;
				if (!fromUser || (presentation == null) || !presentation.canSeek) return;
				long preview = Math.min(presentation.durationMillis, progress * 1000L);
				seekGesture.preview(preview);
				((TextView) header.findViewById(R.id.phone_header_elapsed)).setText(timeText(preview));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				seekToken = binder.captureControlSeekToken();
				if (seekToken != null) seekGesture.begin();
				else seekGesture.cancel();
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				ControlSeekToken token = seekToken;
				long position = seekGesture.finish(token != null);
				seekToken = null;
				if (position >= 0L) binder.seekToIfCurrent(token, position);
				renderLater();
			}
		});
	}

	private void updateTimeline(ControlPresentation presentation) {
		SeekBar progress = header.findViewById(R.id.phone_header_progress);
		View times = header.findViewById(R.id.phone_header_times);
		if (!shouldShowTimeline(presentation.hasMedia, presentation.canSeek)) {
			progress.setVisibility(GONE);
			times.setVisibility(GONE);
			return;
		}
		progress.setVisibility(VISIBLE);
		times.setVisibility(VISIBLE);
		progress.setEnabled(true);
		progress.setAlpha(1f);
		if (!seekGesture.isDragging()) {
			progress.setMax(progressSeconds(presentation.durationMillis));
			progress.setProgress(Math.min(progress.getMax(), progressSeconds(presentation.positionMillis)));
		}
		TextView elapsed = header.findViewById(R.id.phone_header_elapsed);
		if (!seekGesture.isDragging()) {
			elapsed.setText(timeText(presentation.positionMillis));
		}
		((TextView) header.findViewById(R.id.phone_header_duration)).setText(
				timeText(presentation.durationMillis));
	}

	static boolean shouldShowSummary(boolean hasMedia) {
		return hasMedia;
	}

	static boolean shouldShowTimeline(boolean hasMedia, boolean canSeek) {
		return hasMedia && canSeek;
	}

	private void cancelSeek() {
		seekGesture.cancel();
		seekToken = null;
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

	static int aaStatus(@NonNull State state) {
		return switch (state) {
			case DISCONNECTED -> R.string.phone_header_aa_disconnect;
			default -> R.string.phone_header_aa_connected;
		};
	}
}
