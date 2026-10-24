package me.aap.fermata.ui.glass.speedo;

import android.location.Location;

import java.io.File;

/** Writes a trip log with the real recorder for tests in other packages. */
public final class TripLogTestAccess {
	private TripLogTestAccess() {
	}

	public static void record(File dir, Location... fixes) {
		TripLogRecorder r = new TripLogRecorder(dir, () -> true);
		r.event("start", 1_000);
		for (Location l : fixes) r.location(l);
		r.event("stop", 2_000);
	}
}
