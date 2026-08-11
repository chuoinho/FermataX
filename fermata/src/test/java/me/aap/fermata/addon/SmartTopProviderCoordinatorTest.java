package me.aap.fermata.addon;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.os.OsUtils;

public class SmartTopProviderCoordinatorTest {
	private static TestApp app;

	@BeforeClass
	public static void setUpClass() {
		TestUtils.enableTestMode();
		OsUtils.isAndroid();
		app = new TestApp();
		app.onCreate();
	}

	@AfterClass
	public static void tearDownClass() {
		app.onTerminate();
		app = null;
	}

	@Test
	public void hungProviderTimesOutWithoutSuppressingHealthyProvider() throws Exception {
		ProviderAddon hungAddon = new ProviderAddon();
		ProviderAddon healthyAddon = new ProviderAddon();
		SmartTopProviderLease hung = new SmartTopProviderLease(hungAddon, 1L);
		SmartTopProviderLease healthy = new SmartTopProviderLease(healthyAddon, 2L);
		Promise<List<SmartTopCandidate>> never = new Promise<>();
		SmartTopCandidate candidate = candidate(healthy, SmartTopCandidate.Kind.RESUME, 20L);

		SmartTopProviderCoordinator coordinator = new SmartTopProviderCoordinator(
				new Backend(List.of(hung, healthy), hung, never, candidate));
		List<SmartTopProviderResult> result = coordinator.loadCandidates().get(2, SECONDS);

		assertEquals(1, result.size());
		assertEquals(candidate, result.get(0).candidate());
		assertTrue(never.isCancelled());
	}

	@Test
	public void orderingIsTierThenRecencyThenStableIdentity() throws Exception {
		ProviderAddon addon = new ProviderAddon();
		SmartTopProviderLease lease = new SmartTopProviderLease(addon, 3L);
		SmartTopCandidate olderResume = candidate(lease, SmartTopCandidate.Kind.RESUME, 10L);
		SmartTopCandidate newerResume = new SmartTopCandidate(lease.addonClass(),
				lease.lifecycleGeneration(), "opaque-b", SmartTopCandidate.Kind.RESUME, true,
				120_000L, 600_000L, false, "Newer", "", false, false, 30L);
		SmartTopCandidate recommendation = new SmartTopCandidate(lease.addonClass(),
				lease.lifecycleGeneration(), "opaque-c", SmartTopCandidate.Kind.RECOMMENDED, true,
				0L, 0L, false, "Recommended", "", false, false, 100L);
		SmartTopProviderCoordinator coordinator = new SmartTopProviderCoordinator(
				new Backend(List.of(lease), null,
						me.aap.utils.async.Completed.completed(
								List.of(olderResume, recommendation, newerResume)), null));

		List<SmartTopProviderResult> result = coordinator.loadCandidates().get(2, SECONDS);
		assertEquals(List.of(newerResume, olderResume, recommendation),
				result.stream().map(SmartTopProviderResult::candidate).toList());
	}

	private static SmartTopCandidate candidate(SmartTopProviderLease lease,
			SmartTopCandidate.Kind kind, long interaction) {
		return new SmartTopCandidate(lease.addonClass(), lease.lifecycleGeneration(),
				"opaque-a", kind, true, 120_000L, 600_000L, false,
				"Resume", "", false, false, interaction);
	}

	private static final class Backend implements SmartTopProviderCoordinator.Backend {
		private final List<SmartTopProviderLease> leases;
		private final SmartTopProviderLease hung;
		private final FutureSupplier<List<SmartTopCandidate>> response;
		private final SmartTopCandidate healthy;

		private Backend(List<SmartTopProviderLease> leases, SmartTopProviderLease hung,
				FutureSupplier<List<SmartTopCandidate>> response, SmartTopCandidate healthy) {
			this.leases = leases;
			this.hung = hung;
			this.response = response;
			this.healthy = healthy;
		}

		@Override
		public List<SmartTopProviderLease> leases() {
			return leases;
		}

		@Override
		public FutureSupplier<List<SmartTopCandidate>> load(SmartTopProviderLease lease) {
			if (lease == hung) return response;
			if (healthy != null) return me.aap.utils.async.Completed.completed(List.of(healthy));
			return response;
		}

		@Override
		public boolean owns(SmartTopProviderLease lease) {
			return true;
		}

		@Override
		public boolean accepts(SmartTopProviderLease lease, SmartTopCandidate candidate) {
			return lease.addonClass().equals(candidate.addonClass()) &&
					(lease.lifecycleGeneration() == candidate.lifecycleGeneration());
		}

		@Override
		public FutureSupplier<PlayableItem> resolve(DefaultMediaLib lib,
				SmartTopProviderLease lease, SmartTopCandidate candidate) {
			return me.aap.utils.async.Completed.completedNull();
		}
	}

	private static final class ProviderAddon implements FermataAddon {
		@Override
		public int getAddonId() {
			return 0;
		}

		@Override
		public AddonInfo getInfo() {
			throw new UnsupportedOperationException();
		}
	}

	private static final class TestApp extends App {
		private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

		private TestApp() {
			scheduler.setRemoveOnCancelPolicy(true);
		}

		@Override
		public String getLogTag() {
			return "SmartTopProviderCoordinatorTest";
		}

		@Override
		public ScheduledExecutorService getScheduler() {
			return scheduler;
		}

		@Override
		public void onTerminate() {
			scheduler.shutdownNow();
			super.onTerminate();
		}
	}
}
