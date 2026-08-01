package me.aap.fermata.ui.activity;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.speech.SpeechRecognizer;

import androidx.core.content.ContextCompat;

import me.aap.fermata.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

final class SpeechRecognitionSupport {
	private SpeechRecognitionSupport() {
	}

	static FutureSupplier<int[]> checkRecordAudioPermission(MainActivityDelegate activity) {
		if (hasRecordAudioPermission(activity.getContext())) {
			return completed(new int[]{PERMISSION_GRANTED});
		}
		if (activity.isCarActivityNotMirror()) {
			return failed(new IllegalStateException("Audio recording permission is not granted"));
		}
		return activity.getAppActivity().checkPermissions(RECORD_AUDIO);
	}

	static FutureSupplier<Void> requireRecognitionService(Context context) {
		if (SpeechRecognizer.isRecognitionAvailable(context)) return completedVoid();
		IllegalStateException error =
				new IllegalStateException("Speech recognition is not available");
		Log.e(error);
		showAlert(context, R.string.err_speech_recognition_unavailable);
		return failed(error);
	}

	static boolean handleCarVoicePreference(MainActivityDelegate activity) {
		if (!activity.isCarActivityNotMirror()) return false;
		if (!hasRecordAudioPermission(activity.getContext())) {
			showAlert(activity.getContext(), R.string.err_no_audio_record_perm);
			activity.getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
		}
		return true;
	}

	private static boolean hasRecordAudioPermission(Context context) {
		return ContextCompat.checkSelfPermission(context, RECORD_AUDIO) == PERMISSION_GRANTED;
	}
}
