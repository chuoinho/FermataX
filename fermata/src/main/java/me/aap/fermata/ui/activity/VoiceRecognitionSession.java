package me.aap.fermata.ui.activity;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.voice.VoiceEndpointPolicy;
import me.aap.fermata.ui.voice.VoiceIntent;
import me.aap.fermata.ui.voice.VoiceIntentParser;
import me.aap.fermata.ui.voice.VoiceRecognitionController;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;

final class VoiceRecognitionSession implements RecognitionListener,
		VoiceRecognitionController.Host {
	private final MainActivityDelegate activity;
	private final Promise<List<String>> promise;
	private final boolean textInput;
	private final SpeechRecognizer recognizer;
	private final MaterialTextView text;
	private final VoiceRecognitionController controller;
	private final boolean adaptiveAllowed;
	private long generation;
	private PlaybackStateCompat playbackState;
	private MediaEngine pausedEngine;
	private PlayableItem pausedItem;
	private boolean destroyed;

	VoiceRecognitionSession(MainActivityDelegate activity, Promise<List<String>> promise,
			boolean textInput, boolean adaptiveAllowed) {
		this.activity = activity;
		this.promise = promise;
		this.textInput = textInput;
		this.adaptiveAllowed = adaptiveAllowed;
		recognizer = SpeechRecognizer.createSpeechRecognizer(activity.getContext());
		recognizer.setRecognitionListener(this);
		text = new MaterialTextView(activity.getContext());
		controller = new VoiceRecognitionController(VoiceEndpointPolicy.DEFAULT, this);
	}

	void start(Intent intent) {
		MediaSessionCallback callback = activity.getMediaSessionCallback();
		if (callback.isPlaying()) {
			MediaEngine engine = callback.getEngine();
			if ((engine != null) && engine.canPause()) {
				pausedEngine = engine;
				pausedItem = callback.getCurrentItem();
				callback.onPause();
				playbackState = callback.getPlaybackState();
			} else {
				clearPausedPlayback();
			}
		} else {
			clearPausedPlayback();
		}
		generation = controller.start(adaptiveAllowed);
		try {
			recognizer.startListening(intent);
		} catch (RuntimeException ex) {
			Log.e(ex, "Failed to start speech recognition");
			controller.onError(generation, SpeechRecognizer.ERROR_CLIENT);
		}
	}

	void destroy() {
		if (destroyed) return;
		destroyed = true;
		controller.cancel(generation);
		MediaSessionCallback callback = activity.getMediaSessionCallback();
		PlaybackStateCompat state = callback.getPlaybackState();
		if ((playbackState != null) && (callback.getEngine() == pausedEngine) &&
				(callback.getCurrentItem() == pausedItem) &&
				(state.getState() == PlaybackStateCompat.STATE_PAUSED)) {
			if ((state == playbackState) || (state.getPosition() != playbackState.getPosition())) {
				callback.onPlay();
			}
		}
		clearPausedPlayback();
		recognizer.destroy();
		promise.cancel();
		activity.clearVoiceRecognitionSession(this);
	}

	private void clearPausedPlayback() {
		playbackState = null;
		pausedEngine = null;
		pausedItem = null;
	}

	private boolean isCurrent() {
		return !destroyed && activity.isCurrentVoiceRecognitionSession(this) &&
				(generation == controller.getGeneration());
	}

	@Override
	public void onReadyForSpeech(Bundle params) {
		if (!isCurrent()) return;
		controller.onReadyForSpeech(generation);
		activity.getContextMenu().show(builder -> {
			if (!isCurrent()) return;
			Context context = activity.getContext();
			DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
			int size = Math.min(metrics.heightPixels, metrics.widthPixels) / 3;
			LinearLayoutCompat layout = new LinearLayoutCompat(context);
			layout.setOrientation(LinearLayoutCompat.VERTICAL);
			AppCompatImageView image = new AppCompatImageView(context);
			TypedArray attrs = context.getTheme().obtainStyledAttributes(
					new int[]{com.google.android.material.R.attr.colorOnSecondary});
			int imageColor = attrs.getColor(0, 0);
			attrs.recycle();
			image.setMinimumWidth(size);
			image.setMinimumHeight(size);
			image.setImageResource(R.drawable.record_voice);
			image.setImageTintList(ColorStateList.valueOf(imageColor));
			text.setMaxLines(5);
			text.setGravity(Gravity.CENTER);
			text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			attrs = context.getTheme().obtainStyledAttributes(
					new int[]{android.R.attr.textColorSecondary});
			text.setTextColor(ColorStateList.valueOf(attrs.getColor(0, 0)));
			attrs.recycle();
			text.setLayoutParams(new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
			layout.setLayoutParams(new ConstraintLayout.LayoutParams(size, WRAP_CONTENT));
			builder.setView(layout);
			builder.setCloseHandlerHandler(menu -> destroy());
			layout.addView(image);
			layout.addView(text);

			if (textInput) {
				int minKeyboardSize = toIntPx(activity.getContext(), 48);
				int keyboardSize = Math.max(minKeyboardSize, size / 4);
				int margin = toIntPx(activity.getContext(), 1);
				LinearLayoutCompat.LayoutParams layoutParams =
						new LinearLayoutCompat.LayoutParams(keyboardSize, keyboardSize);
				AppCompatImageView keyboard = new AppCompatImageView(context);
				layoutParams.gravity = Gravity.CENTER;
				layoutParams.setMargins(0, margin, 0, margin);
				keyboard.setLayoutParams(layoutParams);
				keyboard.setImageResource(R.drawable.keyboard);
				keyboard.setImageTintList(ColorStateList.valueOf(imageColor));
				layout.setOnClickListener(view -> {
					controller.cancel(generation);
					promise.completeExceptionally(new OperationCanceledException());
					activity.hideActiveMenu();
				});
				layout.addView(keyboard);
			}
		});
	}

	@Override
	public void onResults(Bundle bundle) {
		List<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
		if (results == null) results = List.of();
		controller.onResults(generation, results);
	}

	@Override
	public void onPartialResults(Bundle bundle) {
		if (!isCurrent()) return;
		List<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
		if ((results != null) && !results.isEmpty()) {
			String partial = results.get(0);
			text.setText(partial);
			controller.onPartialResult(generation, partial, isCompletePlaybackCommand(partial));
		}
	}

	@Override
	public void onError(int error) {
		controller.onError(generation, error);
	}

	@Override
	public void onBeginningOfSpeech() {
		controller.onBeginningOfSpeech(generation);
	}

	@Override
	public void onRmsChanged(float rmsdB) {
	}

	@Override
	public void onBufferReceived(byte[] buffer) {
	}

	@Override
	public void onEndOfSpeech() {
		controller.onEndOfSpeech(generation);
	}

	@Override
	public void onEvent(int eventType, Bundle params) {
	}

	@Override
	public Cancellable schedule(Runnable task, long delayMs) {
		return activity.postDelayed(task, delayMs);
	}

	@Override
	public void requestStopListening() {
		if (!isCurrent()) return;
		try {
			recognizer.stopListening();
		} catch (RuntimeException ex) {
			Log.e(ex, "Failed to finalize speech recognition");
			controller.onError(generation, SpeechRecognizer.ERROR_CLIENT);
		}
	}

	@Override
	public void cancelRecognition() {
		if (destroyed) return;
		try {
			recognizer.cancel();
		} catch (RuntimeException ex) {
			Log.e(ex, "Failed to cancel speech recognition");
		}
	}

	@Override
	public void onFinalResults(List<String> results) {
		if (!isCurrent()) return;
		if (!results.isEmpty()) text.setText(results.get(0));
		long completedGeneration = generation;
		activity.postDelayed(() -> {
			if (destroyed || !activity.isCurrentVoiceRecognitionSession(this) ||
					(generation != completedGeneration)) return;
			activity.hideActiveMenu();
			destroy();
		}, 1000);
		promise.complete(results);
	}

	@Override
	public void onFailure(VoiceRecognitionController.Failure failure, int providerError) {
		if (!isCurrent()) return;
		String message = (failure == VoiceRecognitionController.Failure.PROVIDER_ERROR) ?
				"Speech recognition failed with error code " + providerError :
				"Speech recognition failed: " + failure;
		Log.e(message);
		promise.completeExceptionally(new IOException(message));
		activity.hideActiveMenu();
		destroy();

		if ((failure == VoiceRecognitionController.Failure.PROVIDER_ERROR) &&
				(providerError == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)) {
			showAlert(activity.getContext(), R.string.err_no_audio_record_perm);
		}
	}

	private boolean isCompletePlaybackCommand(String phrase) {
		VoiceIntent intent = VoiceIntentParser.parse(phrase,
				activity.getPrefs().getLocalePref());
		return VoiceEndpointPolicy.DEFAULT.isAdaptiveCandidate(intent);
	}
}
