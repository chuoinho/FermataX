package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import me.aap.utils.function.Cancellable;

/** Contract tests across endpoint ownership, parser and session modes. */
public class VoicePipelineIntegrationTest {
	private static final VoiceEndpointPolicy POLICY =
			new VoiceEndpointPolicy(100, 50, 300, 20, 2);

	@Test
	public void longEnglishMediaNameWaitsForFinalResult() {
		Fixture f = new Fixture(true);
		String[] partials = {
				"play", "play youtube", "play youtube video",
				"play youtube video stories told by mouse number two"
		};
		for (String partial : partials) f.partial(partial, Locale.ENGLISH);
		f.scheduler.advanceBy(100);
		assertEquals(0, f.host.stopRequests);
		assertNull(f.host.finalText);

		String result = partials[partials.length - 1];
		f.controller.onEndOfSpeech(f.generation);
		f.controller.onResults(f.generation, List.of(result));
		VoiceIntent intent = VoiceIntentParser.parse(f.host.finalText, Locale.ENGLISH);
		assertEquals(VoiceIntent.Kind.ADDON_SEARCH, intent.getKind());
		assertEquals("youtube", intent.getAddon());
		assertEquals("stories told by mouse number two", intent.getQuery());
	}

	@Test
	public void englishAndVietnameseCommandsUseTheSameFinalOnlyPipeline() {
		assertCommand("pause", Locale.ENGLISH, VoiceIntent.PlaybackAction.PAUSE);
		assertCommand("tam dung", Locale.forLanguageTag("vi-VN"),
				VoiceIntent.PlaybackAction.PAUSE);
	}

	@Test
	public void autoLanguageResultIsParsedAfterProviderFinalizes() {
		Fixture f = new Fixture(true);
		f.partial("phat radio kenh vov", Locale.ROOT);
		f.partial("play radio channel vov traffic", Locale.ROOT);
		f.scheduler.advanceBy(100);
		assertNull(f.host.finalText);

		String providerResult = "play radio channel vov traffic";
		f.controller.onResults(f.generation, List.of(providerResult));
		VoiceIntent intent = VoiceIntentParser.parse(f.host.finalText, Locale.ROOT);
		assertEquals(VoiceIntent.Kind.ADDON_SEARCH, intent.getKind());
		assertEquals("radio", intent.getAddon());
		assertEquals("vov traffic", intent.getQuery());
	}

	@Test
	public void textInputKeepsFinalTextAndNeverUsesAdaptiveStop() {
		Fixture f = new Fixture(false);
		String text = "A long dictated sentence with names and punctuation";
		f.controller.onBeginningOfSpeech(f.generation);
		f.controller.onPartialResult(f.generation, text, true);
		f.controller.onPartialResult(f.generation, text, true);
		f.scheduler.advanceBy(100);
		assertEquals(0, f.host.stopRequests);
		assertNull(f.host.finalText);

		f.controller.onResults(f.generation, List.of(text));
		assertEquals(text, f.host.finalText);
	}

	@Test
	public void selectionResolvesOnlyFromFinalResult() {
		VoiceSession session = new VoiceSession();
		session.beginSelection(List.of(
				new VoiceSession.Option("one", "First", null),
				new VoiceSession.Option("two", "Second", null)), 1_000L);
		Fixture f = new Fixture(false);
		f.controller.onPartialResult(f.generation, "number two", true);
		f.scheduler.advanceBy(100);
		assertEquals(VoiceSession.Mode.SELECTION, session.getMode());
		assertNull(f.host.finalText);

		f.controller.onResults(f.generation, List.of("number two"));
		VoiceSession.Option selected = session.resolveSelection(
				f.host.finalText, Locale.ENGLISH, 1_001L);
		assertEquals("two", selected.getStableId());
		assertEquals(VoiceSession.Mode.COMMAND, session.getMode());
	}

	@Test
	public void changingNoisePartialsCannotCompleteOrStopRecognition() {
		Fixture f = new Fixture(true);
		String[] noise = {"p", "play", "play you", "background", "play youtube video"};
		for (String partial : noise) f.partial(partial, Locale.ENGLISH);
		f.scheduler.advanceBy(100);
		assertEquals(0, f.host.stopRequests);
		assertNull(f.host.finalText);
		assertFalse(f.controller.getState() == VoiceRecognitionController.State.COMPLETED);

		f.controller.onResults(f.generation, List.of("stop"));
		assertEquals("stop", f.host.finalText);
	}

	private static void assertCommand(String phrase, Locale locale,
			VoiceIntent.PlaybackAction expected) {
		Fixture f = new Fixture(true);
		f.partial(phrase, locale);
		f.partial(phrase, locale);
		f.scheduler.advanceBy(20);
		assertEquals(1, f.host.stopRequests);
		assertNull(f.host.finalText);
		f.controller.onResults(f.generation, List.of(phrase));
		assertEquals(expected,
				VoiceIntentParser.parse(f.host.finalText, locale).getPlaybackAction());
	}

	private static final class Fixture {
		final Scheduler scheduler = new Scheduler();
		final Host host = new Host(scheduler);
		final VoiceRecognitionController controller =
				new VoiceRecognitionController(POLICY, host);
		final long generation;

		Fixture(boolean adaptiveAllowed) {
			generation = controller.start(adaptiveAllowed);
		}

		void partial(String text, Locale locale) {
			VoiceIntent intent = VoiceIntentParser.parse(text, locale);
			controller.onPartialResult(controller.getGeneration(), text,
					POLICY.isAdaptiveCandidate(intent));
		}
	}

	private static final class Host implements VoiceRecognitionController.Host {
		private final Scheduler scheduler;
		int stopRequests;
		String finalText;

		Host(Scheduler scheduler) {
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
		}

		@Override
		public void onFinalResults(List<String> results) {
			finalText = results.isEmpty() ? null : results.get(0);
		}

		@Override
		public void onFailure(VoiceRecognitionController.Failure failure, int providerError) {
		}
	}

	private static final class Scheduler {
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
