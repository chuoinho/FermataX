package me.app.fermatax.auto;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.log.Log;

/** Runs teardown participants independently so one failure cannot strand later resources. */
final class AutoShutdownSequence {
	private AutoShutdownSequence() {
	}

	static List<String> run(Step... steps) {
		List<String> failures = new ArrayList<>();
		for (Step step : steps) {
			try {
				step.action.run();
			} catch (Throwable failure) {
				failures.add(step.name + ':' + failure.getClass().getSimpleName());
				Log.e(failure, "Automotive shutdown participant failed: ", step.name);
			}
		}
		return List.copyOf(failures);
	}

	record Step(String name, Runnable action) {
	}
}
