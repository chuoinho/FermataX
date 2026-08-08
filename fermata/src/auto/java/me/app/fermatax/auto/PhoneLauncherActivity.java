package me.app.fermatax.auto;

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

		var target = new Intent(getIntent());
		target.setClass(this, MainActivity.class);
		startActivity(target);
		finish();
	}
}
