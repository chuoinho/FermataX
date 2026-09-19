package me.aap.fermata.addon.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.APP_GET;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.POST;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.RECOVERY;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.REQUEST_GET;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.SAME_DOCUMENT;
import static me.aap.fermata.addon.web.WebUrlSyncObserver.Provenance.UNKNOWN;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Test;

import me.aap.fermata.addon.web.WebUrlSyncObserver.Candidate;
import me.aap.fermata.addon.web.WebUrlSyncObserver.DispatchState;
import me.aap.fermata.addon.web.WebUrlSyncObserver.NavigationEvent;

public class WebUrlSyncObserverTest {
	private static final String A = "https://example.org/a";
	private static final String B = "https://example.org/b?token=x#part";
	private static final String C = "https://example.org/c#spa";

	@Test
	public void baselineAndNavigationTraceEmitOnlyNewTopLevelNavigations() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onFinished(event(7, 1, APP_GET, false, true, A));
		f.scheduler.runAll();
		assertEquals(0, f.received.size());

		f.observer.onStarted(event(7, 2, APP_GET, false, true, B));
		f.observer.onHistory(event(7, 2, APP_GET, false, true, B));
		f.observer.onFinished(event(7, 2, APP_GET, false, true, B));
		f.scheduler.runAll();
		assertEquals(List.of(B), urls(f.received));
		assertEquals(B, f.received.get(0).url());

		f.observer.onHistory(event(7, 3, SAME_DOCUMENT, false, true, C));
		f.scheduler.runAll();
		assertEquals(List.of(B, C), urls(f.received));

		f.observer.onHistory(event(7, 3, SAME_DOCUMENT, false, true, C));
		f.observer.onFinished(event(7, 3, SAME_DOCUMENT, false, true, C));
		f.scheduler.runAll();
		assertEquals(2, f.received.size());

		f.observer.onHistory(event(7, 4, SAME_DOCUMENT, false, true, B));
		f.scheduler.runAll();
		f.observer.onStarted(event(7, 5, APP_GET, true, true, B));
		f.observer.onHistory(event(7, 5, APP_GET, true, true, B));
		f.scheduler.runAll();
		assertEquals(List.of(B, C, B, B), urls(f.received));
		assertTrue(f.received.get(3).reload());

		f.observer.onStarted(event(7, 6, APP_GET, false, false,
				"https://frame.example/"));
		f.observer.onFinished(event(7, 1, APP_GET, false, true, A));
		f.scheduler.runAll();
		assertEquals(4, f.received.size());
	}

	@Test
	public void latestCandidateWinsTheFixedDebounceWindow() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onStarted(event(7, 2, APP_GET, false, true,
				"https://redirect.example/one"));
		f.observer.onStarted(event(7, 2, APP_GET, false, true,
				"https://redirect.example/two"));

		assertEquals(List.of(150L, 150L), f.scheduler.delays());
		assertTrue(f.scheduler.tasks.get(0).cancelled);
		f.scheduler.runAll();
		assertEquals(List.of("https://redirect.example/two"), urls(f.received));
	}

	@Test
	public void dispatchRechecksEveryCapturedLifetimeValue() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		List<UnaryOperator<DispatchState>> staleStates = List.of(
				s -> new DispatchState(false, s.token(), s.sourceGeneration(),
						s.hostGeneration(), s.modeRevision(), s.connectionEpoch()),
				s -> new DispatchState(true, s.token() + 1, s.sourceGeneration(),
						s.hostGeneration(), s.modeRevision(), s.connectionEpoch()),
				s -> new DispatchState(true, s.token(), s.sourceGeneration() + 1,
						s.hostGeneration(), s.modeRevision(), s.connectionEpoch()),
				s -> new DispatchState(true, s.token(), s.sourceGeneration(),
						s.hostGeneration() + 1, s.modeRevision(), s.connectionEpoch()),
				s -> new DispatchState(true, s.token(), s.sourceGeneration(),
						s.hostGeneration(), s.modeRevision() + 1, s.connectionEpoch()),
				s -> new DispatchState(true, s.token(), s.sourceGeneration(),
						s.hostGeneration(), s.modeRevision(), s.connectionEpoch() + 1));

		long sequence = 2;
		for (UnaryOperator<DispatchState> makeStale : staleStates) {
			DispatchState current = f.state;
			f.observer.onStarted(event(7, sequence++, APP_GET, false, true,
					"https://example.org/" + sequence));
			f.state = makeStale.apply(current);
			f.scheduler.runAll();
			f.state = current;
		}

		assertTrue(f.received.isEmpty());
	}

	@Test
	public void rejectsUnsafeEventsAndCancellationCannotReplayPendingWork() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onStarted(event(7, 2, POST, false, true, B));
		f.observer.onStarted(event(7, 3, UNKNOWN, false, true, B));
		f.observer.onStarted(event(7, 4, RECOVERY, false, true, B));
		f.observer.onStarted(event(7, 5, APP_GET, false, true, "javascript:alert(1)"));
		f.observer.onHistory(event(7, 6, SAME_DOCUMENT, false, false, C));
		assertTrue(f.scheduler.tasks.isEmpty());

		NavigationEvent pending = event(7, 7, APP_GET, false, true, B);
		f.observer.onStarted(pending);
		f.observer.cancel();
		assertTrue(f.scheduler.tasks.get(0).cancelled);
		f.scheduler.runAll();
		f.observer.onStarted(pending);
		assertEquals(1, f.scheduler.tasks.size());
		assertTrue(f.received.isEmpty());

		f.observer.onStarted(event(7, 8, APP_GET, false, true, C));
		f.observer.close();
		f.scheduler.runAll();
		f.observer.onStarted(event(7, 9, APP_GET, false, true, B));
		assertTrue(f.received.isEmpty());
	}

	@Test
	public void unsafeNewNavigationSupersedesOlderPendingCandidate() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onStarted(event(7, 2, APP_GET, false, true, B));
		f.observer.onStarted(event(7, 3, POST, false, true, C));
		f.scheduler.runAll();
		assertTrue(f.received.isEmpty());

		f.observer.onStarted(event(7, 2, APP_GET, false, true, B));
		f.observer.onHistory(event(7, 3, SAME_DOCUMENT, false, true, C));
		assertEquals(1, f.scheduler.tasks.size());

		String redirect = "https://example.org/after-post";
		f.observer.onStarted(event(7, 3, REQUEST_GET, false, true, redirect));
		f.scheduler.runAll();
		assertEquals(List.of(redirect), urls(f.received));
	}

	@Test
	public void invalidNewUrlSupersedesOlderPendingCandidate() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onStarted(event(7, 2, APP_GET, false, true, B));
		f.observer.onStarted(event(7, 3, APP_GET, false, true, "javascript:alert(1)"));

		f.scheduler.runAll();

		assertTrue(f.received.isEmpty());
	}

	@Test
	public void onlyCurrentNavigationFailureCancelsPendingCandidate() {
		Fixture f = new Fixture();
		f.observer.baseline(event(7, 1, APP_GET, false, true, A));
		f.observer.onStarted(event(7, 3, APP_GET, false, true, C));
		f.observer.onFailed(event(7, 1, APP_GET, false, true, A));
		f.scheduler.runAll();
		assertEquals(List.of(C), urls(f.received));

		f.observer.onStarted(event(7, 4, APP_GET, false, true, B));
		f.observer.onFailed(event(7, 4, APP_GET, false, true, B));
		f.scheduler.runAll();
		assertEquals(List.of(C), urls(f.received));
	}

	private static NavigationEvent event(long sourceGeneration, long navigationSequence,
			WebUrlSyncObserver.Provenance provenance, boolean reload, boolean mainFrame,
			String url) {
		return new NavigationEvent(sourceGeneration, navigationSequence, provenance, reload,
				mainFrame, url);
	}

	private static List<String> urls(List<Candidate> candidates) {
		return candidates.stream().map(Candidate::url).toList();
	}

	private static final class Fixture {
		final ManualScheduler scheduler = new ManualScheduler();
		final List<Candidate> received = new ArrayList<>();
		DispatchState state = new DispatchState(true, 11, 7, 13, 17, 19);
		final WebUrlSyncObserver observer = new WebUrlSyncObserver(
				() -> state, scheduler, received::add);
	}

	private static final class ManualScheduler implements WebUrlSyncObserver.Scheduler {
		final List<Task> tasks = new ArrayList<>();

		@Override
		public WebUrlSyncObserver.Cancellation schedule(Runnable runnable, long delayMillis) {
			Task task = new Task(runnable, delayMillis);
			tasks.add(task);
			return () -> task.cancelled = true;
		}

		List<Long> delays() {
			return tasks.stream().map(t -> t.delayMillis).toList();
		}

		void runAll() {
			List<Task> scheduled = new ArrayList<>(tasks);
			for (Task task : scheduled) {
				if (!task.ran) {
					task.ran = true;
					task.runnable.run();
				}
			}
		}
	}

	private static final class Task {
		final Runnable runnable;
		final long delayMillis;
		boolean cancelled;
		boolean ran;

		Task(Runnable runnable, long delayMillis) {
			this.runnable = runnable;
			this.delayMillis = delayMillis;
		}
	}
}
