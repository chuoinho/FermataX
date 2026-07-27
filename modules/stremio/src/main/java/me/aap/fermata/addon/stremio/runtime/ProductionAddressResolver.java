package me.aap.fermata.addon.stremio.runtime;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.NetworkLimits;

/** Runs blocking platform DNS on a dedicated, lifecycle-owned executor. */
public final class ProductionAddressResolver implements AddressResolver, AutoCloseable {
	private static final Duration DEFAULT_TIMEOUT = NetworkLimits.DNS_TIMEOUT;
	private final ExecutorService executor;
	private final Lookup lookup;
	private final Duration timeout;
	private final AtomicBoolean closed = new AtomicBoolean();

	public ProductionAddressResolver(ExecutorService executor) {
		this(executor, host -> Arrays.asList(InetAddress.getAllByName(host)), DEFAULT_TIMEOUT);
	}

	ProductionAddressResolver(ExecutorService executor, Lookup lookup, Duration timeout) {
		this.executor = Objects.requireNonNull(executor, "executor");
		this.lookup = Objects.requireNonNull(lookup, "lookup");
		this.timeout = Objects.requireNonNull(timeout, "timeout");
		if (timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException("DNS timeout must be positive");
		}
	}

	@Override
	public List<InetAddress> resolve(String host) throws IOException {
		Objects.requireNonNull(host, "host");
		if (closed.get()) throw new IOException("DNS resolver is closed");

		final Future<List<InetAddress>> task;
		try {
			task = executor.submit(() -> List.copyOf(lookup.resolve(host)));
		} catch (RejectedExecutionException ex) {
			throw new IOException("DNS resolver is closed", ex);
		}

		try {
			return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException ex) {
			task.cancel(true);
			SocketTimeoutException timeoutFailure = new SocketTimeoutException(
					"DNS resolution timed out");
			timeoutFailure.initCause(ex);
			throw timeoutFailure;
		} catch (InterruptedException ex) {
			task.cancel(true);
			Thread.currentThread().interrupt();
			throw new IOException("DNS resolution interrupted", ex);
		} catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof IOException io) throw io;
			throw new IOException("DNS resolution failed", cause);
		}
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) executor.shutdownNow();
	}

	@FunctionalInterface
	interface Lookup {
		List<InetAddress> resolve(String host) throws IOException;
	}
}
