package me.app.fermatax.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class CarService extends CarActivityService {

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MainCarActivity.class;
	}

	@Override
	public void onCreate() {
		Log.d("Creating CarService: " + this);
		super.onCreate();
		AutoConnectionMonitor.hostCreated(this);
	}

	@Override
	public void onDestroy() {
		Log.d("Destroying CarService: " + this);
		try {
			AutoConnectionMonitor.hostDestroyed(this);
		} finally {
			super.onDestroy();
		}
	}
}
