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
		AutomotiveConnectionState.Listener {
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
		binder.addBroadcastListener(this);
		AutomotiveConnectionState.get().addListener(this);
		mode.addListener(modeListener);
		render();
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
		mode.removeListener(modeListener);
		AutomotiveConnectionState.get().removeListener(this);
		binder.removeBroadcastListener(this);
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
		TextView title = header.findViewById(R.id.phone_header_title);
		TextView subtitle = header.findViewById(R.id.phone_header_subtitle);
		title.setText(presentation.hasMedia && (presentation.title.length() > 0) ?
				presentation.title : header.getContext().getString(R.string.phone_header_nothing_playing));
		subtitle.setText(presentation.hasMedia && (presentation.subtitle.length() > 0) ?
				presentation.subtitle : header.getContext().getString(R.string.phone_header_choose_media));
		ImageView artwork = header.findViewById(R.id.phone_header_artwork);
		MediaMetadataCompat metadata = presentation.metadata;
		Bitmap bitmap = (metadata == null) ? null :
				metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
		if (bitmap == null) artwork.setImageResource(R.drawable.audiotrack);
		else artwork.setImageBitmap(bitmap);
		updateTimeline(presentation);
		State state = presentation.automotiveState;
		((TextView) header.findViewById(R.id.phone_header_aa_status)).setText(aaStatus(state));
		header.findViewById(R.id.phone_header_aa_dot).setSelected(state != State.DISCONNECTED);
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
		progress.setEnabled(presentation.canSeek);
		progress.setVisibility(VISIBLE);
		progress.setAlpha(presentation.canSeek ? 1f : 0.45f);
		if (presentation.canSeek && !seekGesture.isDragging()) {
			progress.setMax(progressSeconds(presentation.durationMillis));
			progress.setProgress(Math.min(progress.getMax(), progressSeconds(presentation.positionMillis)));
		}
		else if (!presentation.canSeek) {
			progress.setMax(1);
			progress.setProgress(0);
		}
		TextView elapsed = header.findViewById(R.id.phone_header_elapsed);
		if (!seekGesture.isDragging()) {
			elapsed.setText(presentation.canSeek ? timeText(presentation.positionMillis) : "");
		}
		((TextView) header.findViewById(R.id.phone_header_duration)).setText(
				presentation.canSeek ? timeText(presentation.durationMillis) : "");
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
