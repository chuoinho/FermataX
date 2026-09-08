package me.aap.fermata.media.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import me.aap.utils.pref.BasicPreferenceStore;

import org.junit.Test;

public class AudioEffectsDraftTest {
	@Test
	public void editingTheDraftDoesNotChangeThePersistedProfile() {
		AudioEffectsProfileRepository repository = repository();
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);

		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -6);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[0], 9);

		assertEquals(0, repository.load().preampDb());
		assertEquals(0, repository.load().canonicalCurveDb()[0]);
		assertTrue(draft.isDirty());
	}

	@Test
	public void discardRestoresTheCommittedProfileAfterChildScreenEdits() {
		AudioEffectsProfileRepository repository = repository();
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);

		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.EQUALIZER_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[5], -12);
		draft.discard();

		assertEquals(AudioEffectsProfile.defaults(), draft.snapshot());
		assertFalse(draft.isDirty());
		assertEquals(AudioEffectsProfile.defaults(), repository.load());
	}

	@Test
	public void commitPersistsTheWholeUnifiedProfileAndClearsDirtyState() {
		AudioEffectsProfileRepository repository = repository();
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);

		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, true);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.EQUALIZER_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.BASS_BOOST_STRENGTH, 320);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.LOUDNESS_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.LOUDNESS_GAIN, 180);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.VIRTUALIZER_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH, 640);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.VIRTUALIZER_MODE, 2);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[0], -9);

		AudioEffectsProfile expected = draft.snapshot();
		draft.commit();

		assertEquals(expected, repository.load());
		assertFalse(draft.isDirty());
	}

	@Test
	public void failedApplyKeepsAVisibleFailureStateAndNewEditsPending() {
		AudioEffectsDraft draft = new AudioEffectsDraft(repository());
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);

		assertEquals(AudioEffectsDraft.State.DRAFT, draft.getState());
		assertEquals(-4, draft.beginApply().preampDb());
		assertEquals(AudioEffectsDraft.State.WORKING, draft.getState());
		draft.finishApply(false);

		assertEquals(AudioEffectsDraft.State.FAILED, draft.getState());
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -5);
		assertEquals(AudioEffectsDraft.State.DRAFT, draft.getState());
		assertEquals(-5, draft.snapshot().preampDb());
	}

	@Test
	public void unchangedDraftCanBeExplicitlyApplied() {
		AudioEffectsDraft draft = new AudioEffectsDraft(repository());

		AudioEffectsProfile applied = draft.beginApply();
		assertTrue(applied.equalizerEnabled());
		assertFalse(applied.enabled());
		assertEquals(AudioEffectsProfile.defaults().canonicalCurveDb()[0], applied.canonicalCurveDb()[0]);
	}

	@Test
	public void applyNormalizesLegacyEqualizerOffEvenWhenMasterIsOff() {
		AudioEffectsProfileRepository repository = repository();
		int[] curve = AudioEffectsProfile.flatCurveDb();
		curve[4] = 9;
		repository.save(new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION, false, false,
				curve, -2, false, 0, false, 0, false, 0, 0));
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);

		assertFalse(draft.snapshot().equalizerEnabled());
		assertFalse(repository.load().equalizerEnabled());

		AudioEffectsProfile applied = draft.beginApply();

		assertTrue(applied.equalizerEnabled());
		assertFalse(applied.enabled());
		assertTrue(draft.snapshot().equalizerEnabled());
		assertTrue(repository.load().equalizerEnabled());
		assertEquals(9, repository.load().canonicalCurveDb()[4]);
	}

	@Test
	public void failedApplyCanBeRetriedWithoutChangingTheDraft() {
		AudioEffectsDraft draft = new AudioEffectsDraft(repository());
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);

		assertEquals(-4, draft.beginApply().preampDb());
		draft.finishApply(false);

		assertEquals(-4, draft.beginApply().preampDb());
	}

	@Test
	public void setFlatChangesOnlyTheTenEqualizerBands() {
		AudioEffectsDraft draft = new AudioEffectsDraft(repository());
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, true);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.EQUALIZER_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.BASS_BOOST_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[2], 8);

		draft.setFlat();

		AudioEffectsProfile profile = draft.snapshot();
		assertEquals(0, profile.canonicalCurveDb()[2]);
		assertEquals(-4, profile.preampDb());
		assertTrue(profile.enabled());
		assertTrue(profile.equalizerEnabled());
		assertTrue(profile.bassBoostEnabled());
	}

	@Test
	public void restoreRehydratesTheWorkingProfileWithoutChangingTheRepository() {
		AudioEffectsProfileRepository repository = repository();
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);
		AudioEffectsProfile restored = new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				true, true, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, -3,
				false, 0, false, 0, false, 0,
				android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO);

		draft.restore(restored);

		assertEquals(restored, draft.snapshot());
		assertEquals(AudioEffectsProfile.defaults(), repository.load());
		assertTrue(draft.isDirty());
	}

	@Test
	public void repeatedApplyKeepsLatestEditsPendingWhileFirstApplyIsWorking() {
		AudioEffectsProfileRepository repository = repository();
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);

		assertEquals(-4, draft.beginApply().preampDb());
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -5);

		assertNull(draft.beginApply());
		draft.finishApply(true);
		assertEquals(AudioEffectsDraft.State.DRAFT, draft.getState());
		assertEquals(-5, draft.snapshot().preampDb());
		assertEquals(-4, repository.load().preampDb());
	}

	private static AudioEffectsProfileRepository repository() {
		return new AudioEffectsProfileRepository(new BasicPreferenceStore());
	}
}
