package me.app.fermatax.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class MirrorService extends CarActivityService {
	private static volatile boolean active;
	private MirrorDisplay md;

	public static boolean isActive() {
		return active;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		active = true;
		CarSessionCoordinator.hostCreated(this);
		md = MirrorDisplay.get();
	}

	@Override
	public android.os.IBinder onBind(android.content.Intent intent) {
		Log.i("Binding MirrorService: " + intent);
		active = true;
		CarSessionCoordinator.sessionStarted();
		return super.onBind(intent);
	}

	@Override
	public boolean onUnbind(android.content.Intent intent) {
		Log.i("Unbinding MirrorService: " + intent);
		active = false;
		return true;
	}

	@Override
	public void onRebind(android.content.Intent intent) {
		Log.i("Rebinding MirrorService: " + intent);
		active = true;
		CarSessionCoordinator.sessionStarted();
		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		active = false;
		if (md != null) md.release();
		md = null;
		CarSessionCoordinator.onAutomotiveHostDisconnected(this);
		super.onDestroy();
	}

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MirrorActivity.class;
	}
}
