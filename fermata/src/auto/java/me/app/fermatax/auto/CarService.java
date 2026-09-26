package me.app.fermatax.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class CarService extends CarActivityService {
	private static volatile boolean active;

	public static boolean isActive() {
		return active;
	}

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MainCarActivity.class;
	}

	@Override
	public void onCreate() {
		Log.i("Creating CarService: " + this);
		active = true;
		CarSessionCoordinator.sessionStarted();
		super.onCreate();
		CarSessionCoordinator.hostCreated(this);
	}

	@Override
	public android.os.IBinder onBind(android.content.Intent intent) {
		Log.i("Binding CarService: " + intent);
		active = true;
		CarSessionCoordinator.sessionStarted();
		return super.onBind(intent);
	}

	@Override
	public boolean onUnbind(android.content.Intent intent) {
		Log.i("Unbinding CarService: " + intent);
		active = false;
		return true;
	}

	@Override
	public void onRebind(android.content.Intent intent) {
		Log.i("Rebinding CarService: " + intent);
		active = true;
		CarSessionCoordinator.sessionStarted();
		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		Log.i("Destroying CarService: " + this);
		active = false;
		CarSessionCoordinator.onAutomotiveHostDisconnected(this);
		super.onDestroy();
	}
}
