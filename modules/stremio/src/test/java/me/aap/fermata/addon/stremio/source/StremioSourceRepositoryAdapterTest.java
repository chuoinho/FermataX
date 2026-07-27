package me.aap.fermata.addon.stremio.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.model.source.TransportFingerprint;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceIndexStore.Index;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioSourceRepositoryAdapterTest {
	private static final String UUID_A = "123e4567-e89b-12d3-a456-426614174000";
	private static final String UUID_B = "123e4567-e89b-12d3-a456-426614174001";
	private File directory;
	private StremioRepository repository;
	private FakeIndexStore index;
	private StremioSourceRepositoryAdapter adapter;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("stremio-source-adapter").toFile();
		repository = new StremioRepository(new File(directory, "stremio.db"));
		await(repository.ready());
		index = new FakeIndexStore();
		adapter = new StremioSourceRepositoryAdapter(repository, index);
	}

	@After
	public void tearDown() throws Exception {
		if (repository != null) await(repository.closeAsync());
		delete(directory);
	}

	@Test
	public void commitsAndLoadsRowsInDurableIndexOrder() throws Exception {
		StremioSourceSnapshot empty = await(adapter.load());
		StremioSourceSnapshot first = empty.next(List.of(source(UUID_A, 0)), false);
		await(adapter.commit(empty, first));
		StremioSourceSnapshot second = first.next(List.of(source(UUID_A, 0), source(UUID_B, 1)),
				true);
		await(adapter.commit(first, second));

		StremioSourceSnapshot loaded = await(adapter.load());
		assertEquals(second, loaded);
		assertEquals(List.of(UUID_A, UUID_B), index.value.orderedSourceUuids());
		assertTrue(index.value.cinemetaInstallHandled());
	}

	@Test
	public void staleExpectedSnapshotCannotOverwriteCurrentState() throws Exception {
		StremioSourceSnapshot empty = await(adapter.load());
		StremioSourceSnapshot first = empty.next(List.of(source(UUID_A, 0)), false);
		await(adapter.commit(empty, first));

		Throwable failure = failure(adapter.commit(empty,
				empty.next(List.of(source(UUID_B, 0)), false)));

		assertEquals(Code.CONCURRENT_MODIFICATION, ((StremioSourceException) failure).code());
		assertEquals(first, await(adapter.load()));
		assertNull(await(repository.getSource(UUID_B)));
	}

	@Test
	public void failedIndexCompareAndSetRollsBackAppliedRepositoryRows() throws Exception {
		StremioSourceSnapshot empty = await(adapter.load());
		StremioSourceSnapshot first = empty.next(List.of(source(UUID_A, 0)), false);
		await(adapter.commit(empty, first));
		index.rejectNextCompareAndSet = true;
		StremioSourceSnapshot attempted = first.next(
				List.of(source(UUID_A, 0), source(UUID_B, 1)), false);

		Throwable failure = failure(adapter.commit(first, attempted));

		assertEquals(Code.CONCURRENT_MODIFICATION, ((StremioSourceException) failure).code());
		assertEquals(first, await(adapter.load()));
		assertNotNull(await(repository.getSource(UUID_A)));
		assertNull(await(repository.getSource(UUID_B)));
	}

	@Test
	public void invalidRevisionIsRejectedBeforeAnyWrite() throws Exception {
		StremioSourceSnapshot empty = await(adapter.load());
		StremioSourceSnapshot invalid = new StremioSourceSnapshot(2,
				List.of(source(UUID_A, 0)), false);

		assertTrue(failure(adapter.commit(empty, invalid)) instanceof IllegalArgumentException);
		assertNull(await(repository.getSource(UUID_A)));
	}

	@Test
	public void productionIndexPersistsRowsOrderRevisionAndMarkerInSQLite() throws Exception {
		StremioSourceRepositoryAdapter production =
				new StremioSourceRepositoryAdapter(repository);
		StremioSourceSnapshot empty = await(production.load());
		StremioSourceSnapshot first = empty.next(List.of(source(UUID_A, 0)), false);
		await(production.commit(empty, first));
		StremioSourceSnapshot second = first.next(
				List.of(source(UUID_B, 0), source(UUID_A, 1)), true);
		await(production.commit(first, second));

		await(repository.closeAsync());
		repository = new StremioRepository(new File(directory, "stremio.db"));
		await(repository.ready());

		StremioSourceSnapshot restored = await(
				new StremioSourceRepositoryAdapter(repository).load());
		assertEquals(second, restored);
	}

	private static StremioSourceRecord source(String uuid, int position) {
		return new StremioSourceRecord(uuid, TransportFingerprint.create(
				"https://provider.invalid/" + uuid + "/manifest.json"), "org.test", "Provider",
				"1.0", "https://provider.invalid/manifest.json", "secure:" + uuid, true,
				position, "{}", null, null, 1, 1, null, 1, 1);
	}

	private static Throwable failure(CompletableFuture<?> future) throws Exception {
		try {
			await(future);
			throw new AssertionError("Future completed successfully");
		} catch (ExecutionException failure) {
			Throwable cause = failure.getCause();
			while ((cause instanceof java.util.concurrent.CompletionException) &&
					(cause.getCause() != null)) cause = cause.getCause();
			return cause;
		}
	}

	private static <T> T await(CompletableFuture<T> future) throws Exception {
		return future.get(5, TimeUnit.SECONDS);
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		file.delete();
	}

	private static final class FakeIndexStore implements StremioSourceIndexStore {
		private Index value = new Index(0, List.of(), false);
		private boolean rejectNextCompareAndSet;

		@Override
		public CompletableFuture<Index> load() {
			return CompletableFuture.completedFuture(value);
		}

		@Override
		public CompletableFuture<Boolean> compareAndSet(Index expected, Index replacement) {
			if (rejectNextCompareAndSet) {
				rejectNextCompareAndSet = false;
				return CompletableFuture.completedFuture(false);
			}
			if (!value.equals(expected)) return CompletableFuture.completedFuture(false);
			value = replacement;
			return CompletableFuture.completedFuture(true);
		}
	}
}
