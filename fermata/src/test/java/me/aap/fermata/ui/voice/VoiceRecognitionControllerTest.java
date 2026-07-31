package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import me.aap.utils.function.Cancellable;

public class VoiceRecognitionControllerTest {
	private static final VoiceEndpointPolicy POLICY =
			new VoiceEndpointPolicy(100, 50, 300, 20, 2);

	@Test
	public void normalEndpointCompletesOnlyFinalResult() {
		Fixture f = new Fixture(true);
		f.controller.onReadyForSpeech(f.generation);
		f.controller.onBeginningOfSpeech(f.generation);
		f.controller.onPartialResult(f.generation, "play", true);
		f.controller.onEndOfSpeech(f.generation);
		assertEquals(0, f.host.completions);
		f.controller.onResults(f.generation, List.of("play"));
		assertEquals(1, f.host.completions);
		assertEquals(VoiceRecognitionController.State.COMPLETED, f.controller.getState());
	}

	@Test
	public void finalWithoutEndOfSpeechCompletes() {
		Fixture f = new Fixture(true);
		f.controller.onResults(f.generation, List.of("pause"));
		assertEquals(1, f.host.completions);
	}

	@Test
	public void partialNeverCompletesCommand() {
		Fixture f = new Fixture(true);
		f.controller.onPartialResult(f.generation, "play youtube", true);
		f.scheduler.advanceBy(100);
		assertEquals(0, f.host.completions);
	}

	@Test
	public void duplicateFinalAndErrorAfterFinalAreIgnored() {
		Fixture f = new Fixture(true);
		f.controller.onResults(f.generation, List.of("play"));
		f.controller.onResults(f.generation, List.of("play"));
		f.controller.onError(f.generation, 7);
		assertEquals(1, f.host.completions);
		assertEquals(0, f.host.failures);
	}

	@Test
	public void finalAfterCancelIsIgnored() {
		Fixture f = new Fixture(true);
		f.controller.cancel(f.generation);
		f.controller.onResults(f.generation, List.of("play"));
		assertEquals(0, f.host.completions);
		assertEquals(VoiceRecognitionController.State.CANCELLED, f.controller.getState());
	}

	@Test
	public void oldGenerationCannotAffectNewSession() {
		Fixture f = new Fixture(true);
		long old = f.generation;
		long current = f.controller.start(true);
		f.controller.onResults(old, List.of("old"));
		f.controller.onResults(current, List.of("new"));
		assertEquals(1, f.host.completions);
		assertEquals(List.of("new"), f.host.lastResults);
	}

	@Test
	public void watchdogsFailWithoutCompletingPartialText() {
		Fixture noSpeech = new Fixture(true);
		noSpeech.scheduler.advanceBy(100);
		assertFailure(noSpeech, VoiceRecognitionController.Failure.NO_SPEECH_TIMEOUT);

		Fixture finalResult = new Fixture(true);
		finalResult.controller.onBeginningOfSpeech(finalResult.generation);
		finalResult.controller.onEndOfSpeech(finalResult.generation);
		finalResult.scheduler.advanceBy(50);
		assertFailure(finalResult, VoiceRecognitionController.Failure.FINAL_RESULT_TIMEOUT);

		Fixture hardLimit = new Fixture(true);
		hardLimit.controller.onBeginningOfSpeech(hardLimit.generation);
		hardLimit.scheduler.advanceBy(300);
		assertFailure(hardLimit, VoiceRecognitionController.Failure.SESSION_TIMEOUT);
	}

	@Test
	public void stablePartialOnlyRequestsProviderFinalization() {
		Fixture f = new Fixture(true);
		f.controller.onPartialResult(f.generation, "pause", true);
		f.controller.onPartialResult(f.generation, "pause", true);
		f.scheduler.advanceBy(20);
		assertEquals(1, f.host.stopRequests);
		assertEquals(0, f.host.completions);
		assertEquals(VoiceRecognitionController.State.FINALIZING, f.controller.getState());
		f.controller.onResults(f.generation, List.of("pause"));
		assertEquals(1, f.host.completions);
	}

	@Test
	public void adaptiveFallbackCanBeDisabledForTextAndSelection() {
		Fixture f = new Fixture(false);
		f.controller.onPartialResult(f.generation, "one", true);
		f.controller.onPartialResult(f.generation, "one", true);
		f.scheduler.advanceBy(100);
		assertEquals(0, f.host.stopRequests);
		assertEquals(0, f.host.completions);
	}

	@Test
	public void callbackPermutationsNeverDuplicateCompletion() {
		Random random = new Random(0xF3A7A);
		for (int run = 0; run < 100; run++) {
			Fixture f = new Fixture(random.nextBoolean());
			long stale = f.generation - 1;
			for (int event = 0; event < 24; event++) {
				long token = (random.nextInt(5) == 0) ? stale : f.generation;
				switch (random.nextInt(8)) {
					case 0 -> f.controller.onReadyForSpeech(token);
					case 1 -> f.controller.onBeginningOfSpeech(token);
					case 2 -> f.controller.onPartialResult(token, "pause", true);
					case 3 -> f.controller.onEndOfSpeech(token);
					case 4 -> f.controller.onResults(token, List.of("pause"));
					case 5 -> f.controller.onError(token, 5);
					case 6 -> f.scheduler.advanceBy(random.nextInt(30));
					default -> f.controller.cancel(token);
				}
			}
			f.scheduler.advanceBy(1_000);
			assertTrue("run " + run, f.host.completions <= 1);
			assertTrue("run " + run, f.host.failures <= 1);
			assertTrue("run " + run, (f.host.completions + f.host.failures) <= 1);
		}
	}

	private static void assertFailure(Fixture f, VoiceRecognitionController.Failure failure) {
		assertEquals(0, f.host.completions);
		assertEquals(1, f.host.failures);
		assertEquals(failure, f.host.lastFailure);
	}

	private static final class Fixture {
		final FakeScheduler scheduler = new FakeScheduler();
		final FakeHost host = new FakeHost(scheduler);
		final VoiceRecognitionController controller =
				new VoiceRecognitionController(POLICY, host);
		final long generation;

		Fixture(boolean adaptiveAllowed) {
			generation = controller.start(adaptiveAllowed);
		}
	}

	private static final class FakeHost implements VoiceRecognitionController.Host {
		private final FakeScheduler scheduler;
		int stopRequests;
		int cancelRequests;
		int completions;
		int failures;
		List<String> lastResults = List.of();
		VoiceRecognitionController.Failure lastFailure;

		FakeHost(FakeScheduler scheduler) {
			this.scheduler = scheduler;
		}

		@Override
		public Cancellable schedule(Runnable task, long delayMs) {
			return scheduler.schedule(task, delayMs);
		}

		@Override
		public void requestStopListening() {
			stopRequests++;
		}

		@Override
		public void cancelRecognition() {
			cancelRequests++;
		}

		@Override
		public void onFinalResults(List<String> results) {
			completions++;
			lastResults = results;
		}

		@Override
		public void onFailure(VoiceRecognitionController.Failure failure, int providerError) {
			failures++;
			lastFailure = failure;
		}
	}

	private static final class FakeScheduler {
		private final List<Task> tasks = new ArrayList<>();
		private long now;

		Cancellable schedule(Runnable runnable, long delayMs) {
			Task task = new Task(now + delayMs, runnable);
			tasks.add(task);
			return task;
		}

		void advanceBy(long elapsedMs) {
			long target = now + elapsedMs;
			while (true) {
				Task next = tasks.stream()
						.filter(t -> !t.cancelled && (t.at <= target))
						.min(Comparator.comparingLong(t -> t.at))
						.orElse(null);
				if (next == null) break;
				now = next.at;
				next.cancelled = true;
				next.runnable.run();
			}
			now = target;
		}
	}

	private static final class Task implements Cancellable {
		final long at;
		final Runnable runnable;
		boolean cancelled;

		Task(long at, Runnable runnable) {
			this.at = at;
			this.runnable = runnable;
		}

		@Override
		public boolean cancel() {
			if (cancelled) return false;
			cancelled = true;
			return true;
		}
	}
}
