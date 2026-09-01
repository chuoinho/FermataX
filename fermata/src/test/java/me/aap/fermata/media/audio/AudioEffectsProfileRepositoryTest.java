package me.aap.fermata.media.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.utils.pref.BasicPreferenceStore;

public class AudioEffectsProfileRepositoryTest {
	@Test
	public void newInstallUsesAnExplicitFlatDisabledProfile() {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());

		assertEquals(AudioEffectsProfile.defaults(), repository.load());
		assertEquals(MigrationState.NONE, repository.getMigrationState());
	}

	@Test
	public void profileRoundTripIsLosslessForAFlatOrCustomCurve() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(store);
		AudioEffectsProfile flat = AudioEffectsProfile.defaults();
		repository.save(flat);
		assertEquals(flat, repository.load());

		int[] curve = {-12, -9, -6, -3, 0, 2, 4, 6, 8, 10};
		AudioEffectsProfile custom = new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				true, true, curve, 3, true, 700, true, 450, true, 600, 1);
		repository.save(custom);

		assertEquals(custom, repository.load());
		assertArrayEquals(curve, repository.load().canonicalCurveDb());
	}

	@Test
	public void optionalEffectsAndDisabledProfileRoundTrip() {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());
		AudioEffectsProfile profile = new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				false, false, AudioEffectsProfile.flatCurveDb(), 0, true, 123,
				true, 456, true, 789, 2);

		repository.save(profile);

		assertEquals(profile, repository.load());
		assertFalse(repository.load().enabled());
	}

	@Test
	public void rawLegacyBandsRemainPendingAndAreNeverFabricatedIntoTheCanonicalCurve() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(MediaPrefs.EQ_ENABLED, true);
		legacy.applyIntArrayPref(MediaPrefs.EQ_BANDS, new int[]{-900, -300, 100, 800});
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(legacy);

		AudioEffectsProfile profile = repository.load();
		LegacyAudioEffectsSnapshot snapshot = repository.getLegacySnapshot();

		assertEquals(MigrationState.PENDING_NATIVE_TOPOLOGY, repository.getMigrationState());
		assertArrayEquals(new int[]{-900, -300, 100, 800}, snapshot.rawEqualizerBands());
		assertFalse(profile.equalizerEnabled());
		assertArrayEquals(AudioEffectsProfile.flatCurveDb(), profile.canonicalCurveDb());
	}

	@Test
	public void nativePresetIndexIsPreservedButNotAssumedPortable() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(MediaPrefs.EQ_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.EQ_PRESET, 3);
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(legacy);

		AudioEffectsProfile profile = repository.load();

		assertEquals(MigrationState.PENDING_NATIVE_TOPOLOGY, repository.getMigrationState());
		assertEquals(3, repository.getLegacySnapshot().equalizerPreset());
		assertFalse(profile.equalizerEnabled());
		assertArrayEquals(AudioEffectsProfile.flatCurveDb(), profile.canonicalCurveDb());
	}

	@Test
	public void legacyUserPresetsRemainRawUntilTheirOriginalBandTopologyIsKnown() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(MediaPrefs.EQ_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.EQ_PRESET, -1);
		legacy.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS,
				new String[]{"-400 0 400:Legacy custom"});
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(legacy);

		assertFalse(repository.load().equalizerEnabled());
		assertEquals(MigrationState.PENDING_NATIVE_TOPOLOGY, repository.getMigrationState());
		assertArrayEquals(new String[]{"-400 0 400:Legacy custom"},
				repository.getLegacySnapshot().rawUserPresets());
	}

	@Test
	public void selfContainedLegacyEffectsSeedTheProfileWithoutNeedingEqualizerTopology() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(MediaPrefs.BASS_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.BASS_STRENGTH, 320);
		legacy.applyBooleanPref(MediaPrefs.VOL_BOOST_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.VOL_BOOST_STRENGTH, 180);
		legacy.applyBooleanPref(MediaPrefs.VIRT_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.VIRT_STRENGTH, 500);
		AudioEffectsProfile profile = new AudioEffectsProfileRepository(legacy).load();

		assertEquals(MigrationState.DORMANT,
				new AudioEffectsProfileRepository(legacy).getMigrationState());
		assertTrue(profile.enabled());
		assertTrue(profile.bassBoostEnabled());
		assertEquals(320, profile.bassBoostStrength());
		assertTrue(profile.loudnessEnabled());
		assertEquals(180, profile.loudnessGain());
		assertTrue(profile.virtualizerEnabled());
		assertEquals(500, profile.virtualizerStrength());
	}

	@Test
	public void perTrackAndFolderLegacyDataIsRetainedButNeverSelectedAsTheGlobalProfile() {
		BasicPreferenceStore global = new BasicPreferenceStore();
		BasicPreferenceStore track = new BasicPreferenceStore();
		BasicPreferenceStore folder = new BasicPreferenceStore();
		track.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		track.applyIntArrayPref(MediaPrefs.EQ_BANDS, new int[]{1, 2, 3});
		folder.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		folder.applyIntPref(MediaPrefs.EQ_PRESET, 2);

		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(global);

		assertEquals(AudioEffectsProfile.defaults(), repository.load());
		assertEquals(MigrationState.NONE, repository.getMigrationState());
		assertNull(repository.getLegacySnapshot().rawEqualizerBands());
		assertTrue(track.hasPref(MediaPrefs.AE_ENABLED, false));
		assertArrayEquals(new int[]{1, 2, 3}, track.getIntArrayPref(MediaPrefs.EQ_BANDS));
		assertTrue(folder.hasPref(MediaPrefs.AE_ENABLED, false));
		assertEquals(2, folder.getIntPref(MediaPrefs.EQ_PRESET));
	}

	@Test
	public void savingTheUnifiedProfileRetainsTheLegacyRollbackSnapshot() {
		BasicPreferenceStore legacy = new BasicPreferenceStore();
		legacy.applyBooleanPref(MediaPrefs.AE_ENABLED, true);
		legacy.applyBooleanPref(MediaPrefs.BASS_ENABLED, true);
		legacy.applyIntPref(MediaPrefs.BASS_STRENGTH, 640);
		legacy.applyIntArrayPref(MediaPrefs.EQ_BANDS, new int[]{-300, 0, 300});
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(legacy);
		assertEquals(MigrationState.PENDING_NATIVE_TOPOLOGY, repository.getMigrationState());

		repository.save(new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, true,
				true, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 0, true, 200,
				false, 0, false, 0, 1));

		assertEquals(MigrationState.DORMANT, repository.getMigrationState());
		LegacyAudioEffectsSnapshot snapshot = repository.getLegacySnapshot();
		assertTrue(snapshot.bassBoostEnabled());
		assertEquals(640, snapshot.bassBoostStrength());
		assertArrayEquals(new int[]{-300, 0, 300}, snapshot.rawEqualizerBands());
	}

	@Test
	public void newerPersistedSchemaIsNeverOverwrittenByAnOlderRepository() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyIntPref(AudioEffectsProfileRepository.SCHEMA_VERSION,
				AudioEffectsProfile.SCHEMA_VERSION + 1);

		try {
			new AudioEffectsProfileRepository(store).load();
			fail("Newer persisted profile schema must not be overwritten");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("newer"));
		}
	}
}
