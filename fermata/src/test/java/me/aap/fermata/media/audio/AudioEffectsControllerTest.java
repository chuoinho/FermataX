package me.aap.fermata.media.audio;

import static org.junit.Assert.assertEquals;

import android.media.AudioManager;

import java.lang.reflect.Proxy;
import java.util.EnumSet;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.utils.pref.BasicPreferenceStore;

import org.junit.Test;

public class AudioEffectsControllerTest {
	@Test
	public void doesNotCreateBackendForAnInvalidSession() {
		Fixture fixture = new Fixture();

		fixture.controller.bind(engine(AudioManager.ERROR));

		assertEquals(0, fixture.created);
	}

	@Test
	public void appliesTheActiveProfileOnlyOnceForTheSameSession() {
		Fixture fixture = new Fixture();
		fixture.repository.save(enabledProfile(0));
		MediaEngine engine = engine(41);

		fixture.controller.bind(engine);
		fixture.controller.bind(engine);

		assertEquals(1, fixture.created);
		assertEquals(1, fixture.backends[0].applyCount);
		assertEquals(0, fixture.backends[0].bypassCount);
	}

	@Test
	public void profileChangesReapplyWithoutRecreatingTheBackend() {
		Fixture fixture = new Fixture();
		fixture.repository.save(enabledProfile(-3));
		fixture.controller.bind(engine(41));

		fixture.repository.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -6);

		assertEquals(1, fixture.created);
		assertEquals(2, fixture.backends[0].applyCount);
	}

	@Test
	public void disablingTheProfileBypassesTheCurrentBackend() {
		Fixture fixture = new Fixture();
		fixture.repository.save(enabledProfile(0));
		fixture.controller.bind(engine(41));

		fixture.repository.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, false);

		assertEquals(1, fixture.backends[0].bypassCount);
	}

	@Test
	public void sessionReplacementReleasesBeforeCreatingTheNextBackend() {
		Fixture fixture = new Fixture();
		fixture.repository.save(enabledProfile(0));
		fixture.controller.bind(engine(41));
		fixture.controller.bind(engine(42));

		assertEquals(2, fixture.created);
		assertEquals(1, fixture.backends[0].releaseCount);
		assertEquals(1, fixture.backends[1].applyCount);
	}

	@Test
	public void unbindAndCloseReleaseTheCurrentBackendOnce() {
		Fixture fixture = new Fixture();
		MediaEngine bound = engine(41);
		fixture.controller.bind(bound);

		fixture.controller.unbind(bound);
		fixture.controller.close();

		assertEquals(1, fixture.backends[0].releaseCount);
	}

	@Test
	public void migrationIsPersistedBeforeTheControllerAppliesTheCurrentProfileOnce() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(me.aap.fermata.media.pref.MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(me.aap.fermata.media.pref.MediaPrefs.EQ_ENABLED, true);
		legacy.applyIntArrayPref(me.aap.fermata.media.pref.MediaPrefs.EQ_BANDS,
				new int[]{-500, -400, -300, -200, -100, 0, 100, 200, 300, 400});
		Fixture fixture = new Fixture(legacy, canonicalTopology());

		fixture.controller.bind(engine(41));

		assertEquals(MigrationState.MIGRATED, fixture.repository.getMigrationState());
		assertEquals(1, fixture.backends[0].applyCount);
		assertEquals(0, fixture.backends[0].bypassCount);
	}

	private static AudioEffectsProfile enabledProfile(int preampDb) {
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, true, true,
				AudioEffectsProfile.flatCurveDb(), preampDb, false, 0, false, 0, false, 0, 0);
	}

	private static MediaEngine engine(int sessionId) {
		return (MediaEngine) Proxy.newProxyInstance(MediaEngine.class.getClassLoader(),
				new Class<?>[]{MediaEngine.class}, (proxy, method, args) -> {
					if ("getAudioSessionId".equals(method.getName())) return sessionId;
					if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
					if ("equals".equals(method.getName())) return proxy == args[0];
					return null;
				});
	}

	private static NativeEqualizerTopology canonicalTopology() {
		int[] centers = new int[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		for (int i = 0; i < centers.length; i++) centers[i] = AudioEffectsProfile.CANONICAL_FREQ_HZ[i] * 1_000;
		return NativeEqualizerTopology.create(centers, new short[]{-1_500, 1_500});
	}

	private static final class Fixture {
		final AudioEffectsProfileRepository repository;
		final FakeBackend[] backends = new FakeBackend[2];
		int created;
		final AudioEffectsController controller;

		Fixture() {
			this(new BasicPreferenceStore(), null);
		}

		Fixture(BasicPreferenceStore store, NativeEqualizerTopology topology) {
			repository = new AudioEffectsProfileRepository(store);
			controller = new AudioEffectsController(repository, sessionId -> {
				FakeBackend backend = (topology == null) ? new FakeBackend() : new TopologyBackend(topology);
				backends[created++] = backend;
				return backend;
			});
		}
	}

	private static class FakeBackend implements AudioEffectsBackend {
		int applyCount;
		int bypassCount;
		int releaseCount;

		@Override
		public EnumSet<AudioEffectCapability> getCapabilities() {
			return EnumSet.noneOf(AudioEffectCapability.class);
		}

		@Override
		public void apply(AudioEffectsProfile profile) {
			applyCount++;
		}

		@Override
		public void bypass() {
			bypassCount++;
		}

		@Override
		public void release() {
			releaseCount++;
		}
	}

	private static final class TopologyBackend extends FakeBackend
			implements NativeEqualizerTopologyProvider {
		private final NativeEqualizerTopology topology;

		TopologyBackend(NativeEqualizerTopology topology) {
			this.topology = topology;
		}

		@Override
		public NativeEqualizerTopology getEqualizerTopology() {
			return topology;
		}
	}
}
