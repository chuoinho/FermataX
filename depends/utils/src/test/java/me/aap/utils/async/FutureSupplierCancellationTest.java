package me.aap.utils.async;

import static java.util.concurrent.TimeUnit.SECONDS;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.app.App;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.os.OsUtils;

public class FutureSupplierCancellationTest extends Assertions {
	private static App app;

	@BeforeAll
	public static void setUpClass() {
		TestUtils.enableTestMode();
		// Lock JVM environment detection before the Android Application test double exists.
		OsUtils.isAndroid();
		app = new TestApp();
		app.onCreate();
	}

	@AfterAll
	public static void tearDownClass() {
		app.onTerminate();
		app = null;
	}

	@Test
	public void timeoutCancelsSourceAndKeepsTimeoutFailure() throws Exception {
		TrackingPromise<String> source = new TrackingPromise<>();
		FutureSupplier<String> timed = source.timeout(0);

		source.awaitCancellation();
		awaitCompletion(timed);

		assertInstanceOf(TimeoutException.class, timed.getFailure());
		assertTrue(source.isCancelled());
	}

	@Test
	public void timeoutFallbackStillCancelsSource() throws Exception {
		TrackingPromise<String> source = new TrackingPromise<>();
		FutureSupplier<String> timed = source.timeout(0, () -> "fallback");

		source.awaitCancellation();
		awaitCompletion(timed);

		assertEquals("fallback", timed.get());
		assertFalse(timed.isFailed());
	}

	@Test
	public void closeCancelsMetadataStageBeforeMapperStarts() throws Exception {
		TrackingPromise<String> metadata = new TrackingPromise<>();
		TrackingPromise<String> item = new TrackingPromise<>();
		TrackingPromise<String> download = new TrackingPromise<>();
		AtomicInteger mapperCalls = new AtomicInteger();
		FutureSupplier<String> search = metadata.then(id -> {
			mapperCalls.incrementAndGet();
			return item;
		}).then(value -> download);

		search.close();

		metadata.awaitCancellation();
		assertEquals(0, mapperCalls.get());
		assertFalse(item.isDone());
		assertFalse(download.isDone());
	}

	@Test
	public void closeCancelsActiveItemStage() throws Exception {
		TrackingPromise<String> metadata = new TrackingPromise<>();
		TrackingPromise<String> item = new TrackingPromise<>();
		TrackingPromise<String> download = new TrackingPromise<>();
		AtomicInteger downloadStarts = new AtomicInteger();
		FutureSupplier<String> search = metadata.then(id -> item).then(value -> {
			downloadStarts.incrementAndGet();
			return download;
		});
		metadata.complete("id");

		search.close();

		item.awaitCancellation();
		assertEquals(0, downloadStarts.get());
		assertFalse(download.isDone());
	}

	@Test
	public void closeCancelsActiveDownloadStage() throws Exception {
		TrackingPromise<String> metadata = new TrackingPromise<>();
		TrackingPromise<String> item = new TrackingPromise<>();
		TrackingPromise<String> download = new TrackingPromise<>();
		FutureSupplier<String> search = metadata.then(id -> item).then(value -> download);
		metadata.complete("id");
		item.complete("item");

		search.close();

		download.awaitCancellation();
		assertTrue(search.isCancelled());
	}

	@Test
	public void closeableThenClosesInputAndCancelsActiveChild() throws Exception {
		Promise<TrackingCloseable> source = new Promise<>();
		TrackingPromise<String> child = new TrackingPromise<>();
		TrackingCloseable closeable = new TrackingCloseable();
		FutureSupplier<String> result = source.closeableThen(value -> child);
		source.complete(closeable);

		result.close();

		child.awaitCancellation();
		assertEquals(1, closeable.closeCount.get());
	}

	@Test
	public void closePropagatesThroughAlternateThenCombinators() throws Exception {
		TrackingPromise<String> branchSource = new TrackingPromise<>();
		FutureSupplier<String> branch = branchSource.then(
				value -> new Promise<String>(), error -> new Promise<String>());
		branch.close();
		branchSource.awaitCancellation();

		TrackingPromise<String> ignoredSource = new TrackingPromise<>();
		FutureSupplier<String> ignored = ignoredSource.thenIgnoreResult(Promise::new);
		ignored.close();
		ignoredSource.awaitCancellation();
	}

	private static void awaitCompletion(FutureSupplier<?> future) throws InterruptedException {
		CountDownLatch completed = new CountDownLatch(1);
		future.onCompletion((result, error) -> completed.countDown());
		assertTrue(completed.await(5, SECONDS), "Future did not complete");
	}

	private static final class TrackingPromise<T> extends Promise<T> {
		private final CountDownLatch cancelled = new CountDownLatch(1);

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean result = super.cancel(mayInterruptIfRunning);
			if (result) cancelled.countDown();
			return result;
		}

		void awaitCancellation() throws InterruptedException {
			assertTrue(cancelled.await(5, SECONDS), "Upstream future was not cancelled");
		}
	}

	private static final class TrackingCloseable implements AutoCloseable {
		private final AtomicInteger closeCount = new AtomicInteger();

		@Override
		public void close() {
			closeCount.incrementAndGet();
		}
	}

	private static final class TestApp extends App {
		@Override
		public String getLogTag() {
			return "FutureSupplierCancellationTest";
		}
	}
}
