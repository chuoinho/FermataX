package me.aap.fermata.auto;

import android.content.Context;

import androidx.lifecycle.LifecycleOwner;

/** Mobile builds have no projected UI in the same package. */
public final class PhoneConnectionObservation {
	private PhoneConnectionObservation() {
	}

	public static void observe(Context context, LifecycleOwner owner) {
	}
}
