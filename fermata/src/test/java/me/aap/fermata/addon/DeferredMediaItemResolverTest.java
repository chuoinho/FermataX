package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class DeferredMediaItemResolverTest {
	@Test
	public void disabledAddonIsReportedWithoutRequestingDelivery() {
		DeferredMediaItemResolver resolver = new DeferredMediaItemResolver();
		AtomicInteger requests = new AtomicInteger();

		DeferredMediaItemResult result = resolver.resolve("addon", AddonState.DISABLED,
				() -> {
					requests.incrementAndGet();
					return completedNull();
				}, DeferredMediaItemResolverTest::noItem);

		assertEquals(DeferredMediaItemState.DISABLED, result.getState());
		assertTrue(result.isHandled());
		assertEquals(0, requests.get());
		assertNull(result.getItem().getOrThrow());
	}

	@Test
	public void failedAddonIsReportedWithoutRequestingDelivery() {
		DeferredMediaItemResolver resolver = new DeferredMediaItemResolver();
		AtomicInteger requests = new AtomicInteger();

		DeferredMediaItemResult result = resolver.resolve("addon", AddonState.FAILED,
				() -> {
					requests.incrementAndGet();
					return completedNull();
				}, DeferredMediaItemResolverTest::noItem);

		assertEquals(DeferredMediaItemState.FAILED, result.getState());
		assertEquals(0, requests.get());
	}

	@Test
	public void concurrentLookupsJoinOneDeliveryWithoutBusyRetry() {
		DeferredMediaItemResolver resolver = new DeferredMediaItemResolver();
		Promise<Void> delivery = new Promise<>();
		AtomicInteger requests = new AtomicInteger();
		AtomicInteger resolves = new AtomicInteger();

		DeferredMediaItemResult first = resolver.resolve("addon", AddonState.ENABLED_PENDING,
				() -> {
					requests.incrementAndGet();
					return delivery;
				}, () -> resolvedNull(resolves));
		DeferredMediaItemResult second = resolver.resolve("addon", AddonState.LOADING,
				() -> {
					requests.incrementAndGet();
					return delivery;
				}, () -> resolvedNull(resolves));

		assertEquals(DeferredMediaItemState.LOADING, first.getState());
		assertEquals(DeferredMediaItemState.LOADING, second.getState());
		assertEquals(1, requests.get());
		assertFalse(first.getItem().isDone());
		assertFalse(second.getItem().isDone());

		delivery.complete(null);

		assertTrue(first.getItem().isDone());
		assertTrue(second.getItem().isDone());
		assertEquals(2, resolves.get());
	}

	@Test
	public void loadedAddonStaysOnExistingSynchronousPath() {
		DeferredMediaItemResolver resolver = new DeferredMediaItemResolver();
		AtomicInteger requests = new AtomicInteger();
		AtomicInteger resolves = new AtomicInteger();

		DeferredMediaItemResult result = resolver.resolve("addon", AddonState.LOADED,
				() -> {
					requests.incrementAndGet();
					return completedNull();
				}, () -> resolvedNull(resolves));

		assertSame(DeferredMediaItemResult.notHandled(), result);
		assertEquals(DeferredMediaItemState.NOT_HANDLED, result.getState());
		assertFalse(result.isHandled());
		assertEquals(0, requests.get());
		assertEquals(0, resolves.get());
	}

	@Test
	public void failedDeliveryIsRemovedSoALaterLookupCanRetry() {
		DeferredMediaItemResolver resolver = new DeferredMediaItemResolver();
		Promise<Void> firstDelivery = new Promise<>();
		AtomicInteger requests = new AtomicInteger();

		DeferredMediaItemResult first = resolver.resolve("addon", AddonState.LOADING,
				() -> {
					requests.incrementAndGet();
					return firstDelivery;
				}, DeferredMediaItemResolverTest::noItem);
		firstDelivery.completeExceptionally(new IllegalStateException("delivery failed"));
		assertTrue(first.getItem().isFailed());

		DeferredMediaItemResult retry = resolver.resolve("addon", AddonState.LOADING,
				() -> {
					requests.incrementAndGet();
					return failed(new IllegalStateException("retry failed"));
				}, DeferredMediaItemResolverTest::noItem);

		assertEquals(2, requests.get());
		assertTrue(retry.getItem().isFailed());
	}

	private static FutureSupplier<? extends Item> noItem() {
		return completedNull();
	}

	private static FutureSupplier<? extends Item> resolvedNull(AtomicInteger resolves) {
		resolves.incrementAndGet();
		return completedNull();
	}
}
