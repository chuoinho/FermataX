package me.aap.fermata.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import me.aap.utils.async.Promise;

public class AddonLoaderTest {
	@Test(timeout = 3000)
	public void staleLoadCannotCommitIntoReenabledGeneration() throws Exception {
		assertStaleGenerationIsIsolated(false);
	}

	@Test(timeout = 3000)
	public void staleLoadFailureCannotPoisonReenabledGeneration() throws Exception {
		assertStaleGenerationIsIsolated(true);
	}

	private void assertStaleGenerationIsIsolated(boolean failStaleLoad) throws Exception {
		AddonInfo info = info("generation", GenerationAddon.class, 4);
		GenerationAddon.reset(info, failStaleLoad);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		AddonLoader loader = new AddonLoader(state, new AddonLifecycleCoordinator());
		Object oldGeneration = new Object();
		Object newGeneration = new Object();
		Object transactionLock = new Object();
		AtomicReference<Object> currentGeneration = new AtomicReference<>(oldGeneration);
		AtomicReference<FermataAddon> oldResult = new AtomicReference<>();
		AtomicReference<FermataAddon> newResult = new AtomicReference<>();
		AtomicReference<Throwable> threadFailure = new AtomicReference<>();
		Promise<Boolean> oldActivation = new Promise<>();
		Promise<Boolean> newActivation = new Promise<>();

		Thread oldLoad = new Thread(() -> loadGeneration(loader, state, info, transactionLock,
				currentGeneration, oldGeneration, oldActivation, oldResult, threadFailure));
		oldLoad.start();
		assertTrue(GenerationAddon.firstInstallStarted.await(1, TimeUnit.SECONDS));

		currentGeneration.set(newGeneration);
		Thread newLoad = new Thread(() -> loadGeneration(loader, state, info, transactionLock,
				currentGeneration, newGeneration, newActivation, newResult, threadFailure));
		newLoad.start();
		newLoad.join(1000);

		assertFalse(newLoad.isAlive());
		assertTrue(newActivation.isDoneNotFailed());
		assertFalse(oldActivation.isDone());
		assertSame(newResult.get(), state.getRegistered(info.className));
		assertSame(newResult.get(), state.get(info.className));

		GenerationAddon.releaseFirstInstall.countDown();
		oldLoad.join(1000);

		assertFalse(oldLoad.isAlive());
		assertNull(threadFailure.get());
		assertNull(oldResult.get());
		assertEquals(Boolean.FALSE, oldActivation.peek());
		assertSame(newResult.get(), state.getRegistered(info.className));
		assertSame(newResult.get(), state.get(info.className));
		assertFalse(state.isFailed(info));
		assertEquals(2, GenerationAddon.installCount.get());
		assertEquals(1, GenerationAddon.uninstallCount.get());
	}

	@Test
	public void failedRuntimeRegistrationCleansUpOnlyNewAddon() {
		AddonInfo existingInfo = info("existing", ExistingAddon.class, 1);
		AddonInfo conflictingInfo = info("conflicting", ConflictingAddon.class, 1);
		AddonRuntimeState state = new AddonRuntimeState(
				new AddonRegistry(new AddonInfo[]{existingInfo, conflictingInfo}));
		ExistingAddon existing = new ExistingAddon(existingInfo);
		state.add(existing);
		state.activate(existingInfo, existing);
		ConflictingAddon.info = conflictingInfo;
		ConflictingAddon.installCount = 0;
		ConflictingAddon.uninstallCount = 0;

		AddonLoader loader = new AddonLoader(state, new AddonLifecycleCoordinator());

		assertNull(loader.load(conflictingInfo, () -> true));
		assertSame(existing, state.get(existingInfo.className));
		assertSame(existing, state.get(1));
		assertNull(state.get(conflictingInfo.className));
		assertTrue(state.isFailed(conflictingInfo));
		assertEquals(1, ConflictingAddon.installCount);
		assertEquals(1, ConflictingAddon.uninstallCount);
	}

	@Test
	public void activationCancelledDuringInstallNeverCommitsRuntimeState() {
		AddonInfo info = info("cancelled", ConflictingAddon.class, 2);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		ConflictingAddon.info = info;
		ConflictingAddon.installCount = 0;
		ConflictingAddon.uninstallCount = 0;
		AddonLoader loader = new AddonLoader(state, new AddonLifecycleCoordinator());

		assertNull(loader.load(info, () -> false));
		assertNull(state.get(info.className));
		assertEquals(1, ConflictingAddon.installCount);
		assertEquals(1, ConflictingAddon.uninstallCount);
	}

	@Test
	public void installFailureIsCleanedUpAndMarkedFailed() {
		AddonInfo info = info("failing", FailingAddon.class, 3);
		AddonRuntimeState state =
				new AddonRuntimeState(new AddonRegistry(new AddonInfo[]{info}));
		FailingAddon.info = info;
		FailingAddon.uninstallCount = 0;
		AddonLoader loader = new AddonLoader(state, new AddonLifecycleCoordinator());

		assertNull(loader.load(info, () -> true));
		assertTrue(state.isFailed(info));
		assertNull(state.get(info.className));
		assertEquals(1, FailingAddon.uninstallCount);
	}

	private static AddonInfo info(String module, Class<?> type, int id) {
		return new AddonInfo(module, type.getName(), id, id, id, id,
				false, false, false, false, "");
	}

	private static void loadGeneration(AddonLoader loader, AddonRuntimeState state, AddonInfo info,
										 Object transactionLock, AtomicReference<Object> currentGeneration,
										 Object generation, Promise<Boolean> activation,
										 AtomicReference<FermataAddon> result,
										 AtomicReference<Throwable> threadFailure) {
		try {
			FermataAddon loaded = loader.load(info, (addon, commit) -> {
				synchronized (transactionLock) {
					if (currentGeneration.get() != generation) return false;
					commit.run();
					return true;
				}
			}, () -> {
				synchronized (transactionLock) {
					if (currentGeneration.get() == generation) state.markFailed(info);
				}
			}, addon -> {
				state.activate(info, addon);
				activation.complete(true);
			});
			result.set(loaded);
			if (loaded == null) activation.complete(false);
		} catch (Throwable error) {
			threadFailure.compareAndSet(null, error);
		}
	}

	private record ExistingAddon(AddonInfo info) implements FermataAddon {
		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}
	}

	public static final class ConflictingAddon implements FermataAddon {
		private static AddonInfo info;
		private static int installCount;
		private static int uninstallCount;

		public ConflictingAddon() {
		}

		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}

		@Override
		public void install() {
			installCount++;
		}

		@Override
		public void uninstall() {
			uninstallCount++;
		}
	}

	public static final class FailingAddon implements FermataAddon {
		private static AddonInfo info;
		private static int uninstallCount;

		public FailingAddon() {
		}

		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}

		@Override
		public void install() {
			throw new IllegalStateException("expected");
		}

		@Override
		public void uninstall() {
			uninstallCount++;
		}
	}

	public static final class GenerationAddon implements FermataAddon {
		private static AddonInfo info;
		private static AtomicInteger created;
		private static AtomicInteger installCount;
		private static AtomicInteger uninstallCount;
		private static CountDownLatch firstInstallStarted;
		private static CountDownLatch releaseFirstInstall;
		private static boolean failFirstInstall;
		private final int instance;

		public GenerationAddon() {
			instance = created.incrementAndGet();
		}

		static void reset(AddonInfo addonInfo, boolean failFirst) {
			info = addonInfo;
			created = new AtomicInteger();
			installCount = new AtomicInteger();
			uninstallCount = new AtomicInteger();
			firstInstallStarted = new CountDownLatch(1);
			releaseFirstInstall = new CountDownLatch(1);
			failFirstInstall = failFirst;
		}

		@Override
		public int getAddonId() {
			return info.addonId;
		}

		@Override
		public AddonInfo getInfo() {
			return info;
		}

		@Override
		public void install() {
			installCount.incrementAndGet();
			if (instance != 1) return;
			firstInstallStarted.countDown();
			try {
				if (!releaseFirstInstall.await(2, TimeUnit.SECONDS))
					throw new AssertionError("Timed out waiting to release stale load");
				if (failFirstInstall) throw new IllegalStateException("Expected stale failure");
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new AssertionError(error);
			}
		}

		@Override
		public void uninstall() {
			uninstallCount.incrementAndGet();
		}
	}
}
