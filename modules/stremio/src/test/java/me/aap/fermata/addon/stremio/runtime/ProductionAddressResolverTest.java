package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class ProductionAddressResolverTest {
	@Test
	public void lookupRunsOnOwnedDnsThread() throws Exception {
		var executor = Executors.newSingleThreadExecutor(task ->
				new Thread(task, "stremio-dns-test"));
		AtomicReference<String> lookupThread = new AtomicReference<>();
		var resolver = new ProductionAddressResolver(executor, host -> {
			lookupThread.set(Thread.currentThread().getName());
			return List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}));
		}, Duration.ofSeconds(1));
		String caller = Thread.currentThread().getName();

		try {
			assertEquals(1, resolver.resolve("provider.invalid").size());
			assertEquals("stremio-dns-test", lookupThread.get());
			assertNotEquals(caller, lookupThread.get());
		} finally {
			resolver.close();
		}
	}

	@Test
	public void timeoutCancelsLookupAndCloseRejectsNewWork() throws Exception {
		var executor = Executors.newSingleThreadExecutor();
		CountDownLatch interrupted = new CountDownLatch(1);
		var resolver = new ProductionAddressResolver(executor, host -> {
			try {
				Thread.sleep(10_000);
			} catch (InterruptedException ex) {
				interrupted.countDown();
				Thread.currentThread().interrupt();
			}
			return List.of();
		}, Duration.ofMillis(20));

		assertThrows(SocketTimeoutException.class,
				() -> resolver.resolve("provider.invalid"));
		assertTrue(interrupted.await(1, TimeUnit.SECONDS));
		resolver.close();
		assertThrows(java.io.IOException.class,
				() -> resolver.resolve("provider.invalid"));
	}
}
