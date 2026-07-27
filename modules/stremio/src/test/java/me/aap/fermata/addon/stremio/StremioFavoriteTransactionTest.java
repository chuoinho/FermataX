package me.aap.fermata.addon.stremio;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class StremioFavoriteTransactionTest {
	@Test
	public void successfulCollectionAndDatabaseWriteNeedNoRollback() throws Exception {
		AtomicInteger rollbacks = new AtomicInteger();

		StremioAddon.synchronizeFavoriteTransaction(completedVoid(),
				() -> completedVoid(), () -> {
					rollbacks.incrementAndGet();
					return completedVoid();
				}).get();

		assertEquals(0, rollbacks.get());
	}

	@Test
	public void databaseFailureRollsBackCollectionAndPreservesOriginalError() {
		AtomicInteger rollbacks = new AtomicInteger();
		IllegalStateException database = new IllegalStateException("database");

		ExecutionException failure = org.junit.Assert.assertThrows(ExecutionException.class,
				() -> StremioAddon.synchronizeFavoriteTransaction(completedVoid(),
						() -> failed(database), () -> {
							rollbacks.incrementAndGet();
							return completedVoid();
						}).get());

		assertSame(database, failure.getCause());
		assertEquals(1, rollbacks.get());
	}

	@Test
	public void collectionFailureDoesNotRunDatabaseOrRollback() {
		AtomicInteger databaseWrites = new AtomicInteger();
		AtomicInteger rollbacks = new AtomicInteger();

		org.junit.Assert.assertThrows(ExecutionException.class,
				() -> StremioAddon.synchronizeFavoriteTransaction(
						failed(new IllegalStateException("collection")), () -> {
							databaseWrites.incrementAndGet();
							return completedVoid();
						}, () -> {
							rollbacks.incrementAndGet();
							return completedVoid();
						}).get());

		assertEquals(0, databaseWrites.get());
		assertEquals(0, rollbacks.get());
	}
}
