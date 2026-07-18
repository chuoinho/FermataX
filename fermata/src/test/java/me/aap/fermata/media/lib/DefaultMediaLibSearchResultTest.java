package me.aap.fermata.media.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.async.Promise;

public class DefaultMediaLibSearchResultTest {
	@Test
	public void detachesBeforeDeliveringSuccessfulSearch() {
		RecordingResult<String> result = new RecordingResult<>();
		DefaultMediaLib.DetachedSearchResult<String> delivery =
				new DefaultMediaLib.DetachedSearchResult<>(result);

		assertEquals(1, result.detachCount.get());
		assertEquals(0, result.deliveryCount.get());

		delivery.complete("item", null);

		assertEquals(1, result.deliveryCount.get());
		assertNull(result.error);
		assertEquals(1, result.value.size());
		assertEquals("item", result.value.get(0));
		assertTrue(result.detachedBeforeDelivery);
	}

	@Test
	public void successfulMissKeepsExistingEmptyResult() {
		RecordingResult<String> result = new RecordingResult<>();
		DefaultMediaLib.DetachedSearchResult<String> delivery =
				new DefaultMediaLib.DetachedSearchResult<>(result);

		delivery.complete(null, null);

		assertNotNull(result.value);
		assertTrue(result.value.isEmpty());
		assertNull(result.error);
	}

	@Test
	public void failureAndCancellationCompleteWithError() {
		assertErrorDelivery(new IllegalStateException("query failed"));
		assertErrorDelivery(new CancellationException("query cancelled"));
	}

	@Test
	public void concurrentTerminalCallbacksDeliverExactlyOnce() throws InterruptedException {
		RecordingResult<String> result = new RecordingResult<>();
		DefaultMediaLib.DetachedSearchResult<String> delivery =
				new DefaultMediaLib.DetachedSearchResult<>(result);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Thread success = completeOnStart(ready, start, () -> delivery.complete("item", null));
		Thread failure = completeOnStart(ready, start,
				() -> delivery.complete(null, new IllegalStateException("late failure")));

		success.start();
		failure.start();
		ready.await();
		start.countDown();
		success.join();
		failure.join();

		assertEquals(1, result.deliveryCount.get());
		assertTrue((result.value != null) ^ (result.error != null));
	}

	@Test
	public void closingRegistryCancelsSearchAndCompletesDetachedResultOnce() {
		DefaultMediaLib.SearchRequestRegistry registry =
				new DefaultMediaLib.SearchRequestRegistry();
		Promise<String> search = new Promise<>();
		RecordingResult<String> result = new RecordingResult<>();
		DefaultMediaLib.DetachedSearchResult<String> delivery =
				new DefaultMediaLib.DetachedSearchResult<>(result);
		search.onCompletion((value, error) -> {
			registry.finish(search);
			delivery.complete(value, error);
		});
		registry.add(search);

		assertTrue(registry.close());

		assertTrue(search.isCancelled());
		assertEquals(1, result.deliveryCount.get());
		assertNotNull(result.error);
		assertFalse(search.complete("late result"));
		assertEquals(1, result.deliveryCount.get());
		assertFalse(registry.close());
	}

	@Test
	public void closedRegistryCancelsNewSearchImmediately() {
		DefaultMediaLib.SearchRequestRegistry registry =
				new DefaultMediaLib.SearchRequestRegistry();
		Promise<String> search = new Promise<>();
		assertTrue(registry.close());

		registry.add(search);

		assertTrue(search.isCancelled());
	}

	private static void assertErrorDelivery(Throwable error) {
		RecordingResult<String> result = new RecordingResult<>();
		DefaultMediaLib.DetachedSearchResult<String> delivery =
				new DefaultMediaLib.DetachedSearchResult<>(result);

		delivery.complete(null, error);

		assertEquals(1, result.deliveryCount.get());
		assertNull(result.value);
		assertNotNull(result.error);
		assertTrue(result.detachedBeforeDelivery);
	}

	private static Thread completeOnStart(CountDownLatch ready, CountDownLatch start, Runnable task) {
		return new Thread(() -> {
			ready.countDown();
			try {
				start.await();
				task.run();
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private static final class RecordingResult<T> implements MediaLibResult<List<T>> {
		final AtomicInteger detachCount = new AtomicInteger();
		final AtomicInteger deliveryCount = new AtomicInteger();
		volatile List<T> value;
		volatile Bundle error;
		volatile boolean detachedBeforeDelivery;

		@Override
		public void sendResult(List<T> value, Bundle error) {
			detachedBeforeDelivery = detachCount.get() == 1;
			this.value = value;
			this.error = error;
			deliveryCount.incrementAndGet();
		}

		@Override
		public void detach() {
			detachCount.incrementAndGet();
		}
	}
}
