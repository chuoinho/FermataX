package me.app.fermatax.auto;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.log.Log;

import me.aap.fermata.media.service.AutomotiveRuntimeGate;

/** Runs teardown participants independently so one failure cannot strand later resources. */
final class AutoShutdownSequence {
	private AutoShutdownSequence() {
	}

	static List<String> run(Step... steps) {
		return run(0L, steps);
	}

	static List<String> run(long expectedGeneration, Step... steps) {
		List<String> failures = new ArrayList<>();
		for (Step step : steps) {
			if ((expectedGeneration > 0L) && !AutomotiveRuntimeGate.isTearingDownGeneration(expectedGeneration)) {
				Log.i("AutoShutdownSequence: Aborting teardown for generation ", expectedGeneration,
						"; superseded by a newer active generation");
				break;
			}
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
