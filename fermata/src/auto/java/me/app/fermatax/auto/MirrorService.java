package me.app.fermatax.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

/**
 * @author Andrey Pavlenko
 */
public class MirrorService extends CarActivityService {
	private MirrorDisplay md;

	@Override
	public void onCreate() {
		super.onCreate();
		AutoConnectionMonitor.hostCreated(this);
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		if (md != null) md.release();
		md = null;
		AutoConnectionMonitor.hostDestroyed(this);
		super.onDestroy();
	}

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MirrorActivity.class;
	}
}
