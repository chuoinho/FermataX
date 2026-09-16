package me.aap.fermata.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;

import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.car.app.connection.CarConnection;

import me.aap.utils.log.Log;

/** Passive phone-owned observation of the official projected connection signal. */
public final class PhoneConnectionObservation {
	private PhoneConnectionObservation() {
	}

	public static void observe(Context context, LifecycleOwner owner) {
		try {
			new CarConnection(context.getApplicationContext()).getType().observe(owner, type -> {
				if (type != null) AutomotiveConnectionState.get().connectionChanged(
						type == CONNECTION_TYPE_PROJECTION);
			});
		} catch (RuntimeException error) {
			Log.e(error, "Failed to observe Android Auto connection from phone UI");
		}
	}
}
