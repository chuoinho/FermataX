package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.utils.pref.PreferenceStore;

/** Standard preference-row action for committing and applying the shared EQ draft. */
public final class AudioEffectsApplyView extends LinearLayout implements AudioEffectsDraft.Listener {
	private final AudioEffectsDraft draft;
	private final MediaSessionCallback callback;
	private final Button cancel;
	private final Button apply;
	private final TextView status;
	private final PreferenceStore.Listener masterDisable = this::onDraftChanged;

	public AudioEffectsApplyView(Context context, AudioEffectsDraft draft,
			MediaSessionCallback callback) {
		super(context);
		this.draft = draft;
		this.callback = callback;
		setOrientation(VERTICAL);
		setGravity(Gravity.CENTER_VERTICAL);
		setBackgroundColor(resolveColor(context, android.R.attr.colorBackground, Color.TRANSPARENT));
		status = new TextView(context);
		status.setTextColor(Color.GRAY);
		status.setGravity(Gravity.CENTER_VERTICAL);
		status.setMinHeight(dp(40));
		addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		LinearLayout buttons = new LinearLayout(context);
		buttons.setOrientation(HORIZONTAL);
		buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
		cancel = new Button(context);
		cancel.setText(R.string.cancel);
		cancel.setAllCaps(false);
		cancel.setMinHeight(dp(48));
		cancel.setOnClickListener(v -> draft.discard());
		buttons.addView(cancel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
		apply = new Button(context);
		apply.setText(R.string.audio_effects_apply);
		apply.setAllCaps(false);
		apply.setMinHeight(dp(48));
		apply.setOnClickListener(v -> startApply());
		buttons.addView(apply, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
		addView(buttons, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		setPadding(0, 0, 0, dp(4));
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		draft.addListener(this);
		draft.getStore().addBroadcastListener(masterDisable);
	}

	@Override
	protected void onDetachedFromWindow() {
		draft.getStore().removeBroadcastListener(masterDisable);
		draft.removeListener(this);
		super.onDetachedFromWindow();
	}

	private void onDraftChanged(PreferenceStore ignored, List<PreferenceStore.Pref<?>> changed) {
		for (PreferenceStore.Pref<?> pref : changed) {
			if ((pref == AudioEffectsProfileRepository.ENABLED) && !draft.snapshot().enabled()) {
				callback.disableAudioEffectsEmergency();
				return;
			}
		}
	}

	@Override
	public void onStateChanged(AudioEffectsDraft.State state) {
		apply.setEnabled(!draft.isApplying());
		cancel.setEnabled(!draft.isApplying());
		int message = switch (state) {
			case CLEAN -> 0;
			case DRAFT -> R.string.audio_effects_draft;
			case WORKING -> R.string.audio_effects_applying;
			case APPLIED -> R.string.audio_effects_applied;
			case FAILED -> R.string.audio_effects_apply_failed;
		};
		status.setVisibility((message == 0) ? GONE : VISIBLE);
		if (message != 0) status.setText(message);
	}

	private static int resolveColor(Context context, int attribute, int fallback) {
		android.util.TypedValue value = new android.util.TypedValue();
		if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
		return (value.resourceId == 0) ? value.data :
				androidx.core.content.ContextCompat.getColor(context, value.resourceId);
	}

	private void startApply() {
		if (draft.isApplying()) return;
		AudioEffectsProfile profile = draft.snapshot();
		callback.deferAudioEffectsProfile(profile);
		final AudioEffectsProfile committed;
		try {
			committed = draft.beginApply();
		} catch (RuntimeException error) {
			callback.cancelDeferredAudioEffectsProfile();
			return;
		}
		if (committed == null) {
			callback.cancelDeferredAudioEffectsProfile();
			return;
		}
		try {
			callback.applyAudioEffects(committed).onCompletion((ignored, error) ->
					callback.getHandler().post(() -> draft.finishApply(error == null)));
		} catch (RuntimeException error) {
			draft.finishApply(false);
		}
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
