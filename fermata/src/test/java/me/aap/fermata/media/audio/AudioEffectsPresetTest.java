package me.aap.fermata.media.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import me.aap.utils.pref.BasicPreferenceStore;

import org.junit.Test;

public class AudioEffectsPresetTest {
	@Test
	public void tableContainsFiveGenreSuggestionsAndCustom() {
		assertEquals(6, AudioEffectsPreset.values().length);
		assertEquals(AudioEffectsPreset.CUSTOM,
				AudioEffectsPreset.values()[AudioEffectsPreset.values().length - 1]);
		for (AudioEffectsPreset preset : AudioEffectsPreset.values()) {
			if (!preset.hasCurve()) continue;
			assertEquals(AudioEffectsProfile.CANONICAL_FREQ_HZ.length, preset.curveDb().length);
			assertTrue(preset.maximumBoostDb() <= 3);
			for (int gain : preset.curveDb()) {
				assertTrue(gain >= AudioEffectsProfile.MIN_CANONICAL_DB);
				assertTrue(gain <= AudioEffectsProfile.MAX_CANONICAL_DB);
			}
		}
	}

	@Test
	public void presetCurveArraysAreImmutableAndUseCanonicalFrequencies() {
		int[] curve = AudioEffectsPreset.POP.curveDb();
		curve[0] = 15;
		assertEquals(2, AudioEffectsPreset.POP.curveDb()[0]);
		assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
				AudioEffectsPreset.FLAT.curveDb());
	}

	@Test
	public void selectingPresetKeepsOtherEffectsAndAddsOnlyRequiredHeadroom() {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, true);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.BASS_BOOST_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -1);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.BASS_BOOST_STRENGTH, 320);
		AudioEffectsProfile before = draft.snapshot();

		draft.applyPreset(AudioEffectsPreset.DANCE);
		AudioEffectsProfile after = draft.snapshot();

		assertEquals(-AudioEffectsPreset.DANCE.maximumBoostDb(), after.preampDb());
		assertTrue(after.bassBoostEnabled());
		assertEquals(before.bassBoostStrength(), after.bassBoostStrength());
		assertArrayEquals(AudioEffectsPreset.DANCE.curveDb(), after.canonicalCurveDb());

		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -6);
		draft.applyPreset(AudioEffectsPreset.POP);
		assertEquals(-6, draft.snapshot().preampDb());
	}

	@Test
	public void presetIsPersistedOnlyByExplicitApply() {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);
		draft.applyPreset(AudioEffectsPreset.ROCK);

		assertArrayEquals(AudioEffectsProfile.flatCurveDb(), repository.load().canonicalCurveDb());

		draft.beginApply();

		assertArrayEquals(AudioEffectsPreset.ROCK.curveDb(), repository.load().canonicalCurveDb());
	}

	@Test
	public void flatPresetPreservesPreampAndOtherEffects() {
		AudioEffectsProfileRepository repository = new AudioEffectsProfileRepository(
				new BasicPreferenceStore());
		AudioEffectsDraft draft = new AudioEffectsDraft(repository);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, true);
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.LOUDNESS_ENABLED, true);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.PREAMP_DB, -4);
		draft.getStore().applyIntPref(AudioEffectsProfileRepository.CANONICAL_CURVE_DB[2], 3);

		draft.applyPreset(AudioEffectsPreset.FLAT);

		assertEquals(-4, draft.snapshot().preampDb());
		assertTrue(draft.snapshot().loudnessEnabled());
		assertArrayEquals(AudioEffectsProfile.flatCurveDb(), draft.snapshot().canonicalCurveDb());
		assertTrue(draft.isDirty());
	}

	@Test
	public void matchingCurveReturnsPresetAndUnknownCurveIsCustom() {
		assertEquals(AudioEffectsPreset.ROCK,
				AudioEffectsPreset.match(AudioEffectsPreset.ROCK.curveDb()));
		int[] custom = AudioEffectsPreset.ROCK.curveDb();
		custom[0]++;
		assertEquals(AudioEffectsPreset.CUSTOM, AudioEffectsPreset.match(custom));
	}
}
