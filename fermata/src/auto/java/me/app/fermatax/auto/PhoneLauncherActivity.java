package me.app.fermatax.auto;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivity;

/** Phone launcher entry that resumes an AA fullscreen permission request when one is pending. */
public final class PhoneLauncherActivity extends Activity {
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (ProjectionActivity.resumePendingRequest(this)) {
			finish();
			return;
		}

		// Do not clone the launcher intent. Some OEM launchers attach task flags, categories,
		// bounds and extras that make ActivityTaskManager deliver the request back to this
		// no-display trampoline. Finishing it then leaves an empty task and returns to Home.
		var target = new Intent(this, MainActivity.class);
		target.setAction(Intent.ACTION_MAIN);
		target.addFlags(FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(target);
		finish();
	}
}
