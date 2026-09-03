package me.aap.fermata.media.audio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.Test;

public class AudioEffectsProfileArchitectureTest {
	@Test
	public void profileAndSettingsConfigurationAreIndependentOfTheActiveEngine() throws Exception {
		assertFalse(source("media/audio/AudioEffectsProfileRepository.java").contains("MediaEngine"));
		assertFalse(source("media/audio/AudioEffectsProfile.java").contains("MediaEngine"));
		assertFalse(source("ui/fragment/AudioEffectsPrefsBuilder.java").contains("MediaEngine"));
		assertTrue(source("ui/fragment/PlaybackPrefsBuilder.java")
				.contains("AudioEffectsPrefsBuilder.add"));
	}

	@Test
	public void controlPanelNoLongerExposesAnIndependentAudioEffectsEditor() throws Exception {
		assertFalse(source("ui/view/ControlPanelView.java").contains("audio_effects_fragment"));
	}

	@Test
	public void mediaSessionCallbackDoesNotReferenceTheUnifiedProfile() throws Exception {
		assertFalse(source("media/service/MediaSessionCallback.java")
				.contains("AudioEffectsProfile"));
	}

	@Test
	public void nativeSessionControllerIsTheOnlyNormalPlaybackAuthority() throws Exception {
		String callback = source("media/service/MediaSessionCallback.java");
		assertTrue(callback.contains("AudioEffectsController"));
		assertFalse(callback.contains("AudioEffectsLegacyApplier.apply"));
		assertTrue(source("media/audio/AudioEffectsController.java").contains("getAudioSessionId()"));
	}

	@Test
	public void nativeEnginesOnlyExposeTheirAudioSession() throws Exception {
		assertFalse(source("media/engine/MediaPlayerEngine.java").contains("AudioEffects.create"));
		assertFalse(moduleSource("exoplayer/src/main/java/me/aap/fermata/engine/exoplayer/ExoPlayerEngine.java")
				.contains("AudioEffects.create"));
		assertFalse(moduleSource("vlc/src/main/java/me/aap/fermata/engine/vlc/VlcEngine.java")
				.contains("AudioEffects.create"));
	}

	@Test
	public void nativeSessionBackendCannotAttachEffectsToSessionZero() throws Exception {
		String backend = source("media/audio/NativeSessionAudioEffectsBackend.java");
		assertTrue(backend.contains("return audioSessionId > 0"));
		assertFalse(backend.contains("new Equalizer(EFFECT_PRIORITY, 0)"));
		assertFalse(backend.contains("new DynamicsProcessing(0)"));
		assertFalse(backend.contains("new android.media.audiofx.DynamicsProcessing(0)"));
	}

	@Test
	public void topologyMigrationPersistsAProfileButNeverBecomesAnotherEffectAuthority() throws Exception {
		assertFalse(source("media/audio/AudioEffectsProfileRepository.java")
				.contains("AudioEffectsBackend"));
		assertFalse(source("media/audio/NativeToCanonicalEqualizerMapper.java")
				.contains("AudioEffectsBackend"));
		assertTrue(source("media/audio/AudioEffectsController.java")
				.contains("migratePendingLegacyEqualizer"));
	}

	@Test
	public void audioEqualizerTitleIsTranslatedInEverySupportedLocale() throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resources = root.resolve("src/main/res");
		if (!Files.isDirectory(resources)) resources = root.resolve("fermata/src/main/res");
		try (Stream<Path> directories = Files.list(resources)) {
			directories.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().startsWith("values"))
					.map(path -> path.resolve("strings.xml"))
					.filter(Files::isRegularFile)
					.forEach(path -> {
						try {
							String strings = new String(Files.readAllBytes(path), UTF_8);
							assertTrue(path.toString(), strings.contains("name=\"audio_equalizer\""));
							assertTrue(path.toString(), strings.contains("name=\"preamp\""));
						} catch (Exception error) {
							throw new AssertionError(path.toString(), error);
						}
					});
		}
	}

	@Test
	public void unifiedSettingsExposeNegativeOnlyPreampThroughTheProfileRepository()
			throws Exception {
		String builder = source("ui/fragment/AudioEffectsPrefsBuilder.java");

		assertFalse(builder.contains("PreferenceSet preamp"));
		assertTrue(builder.contains("AudioEffectsProfileRepository.PREAMP_DB"));
		assertTrue(builder.contains("R.string.preamp"));
		assertTrue(builder.contains("o.seekMin = AudioEffectsProfile.MIN_CANONICAL_DB"));
		assertTrue(builder.contains("o.seekMax = 0"));
	}

	@Test
	public void preampAcceptsSignedNumericInput() throws Exception {
		String builder = source("ui/fragment/AudioEffectsPrefsBuilder.java");

		assertTrue(builder.contains("InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED"));
	}

	private static String source(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata").resolve(relativePath);
		if (!Files.isRegularFile(file)) file = root.resolve("fermata/src/main/java/me/aap/fermata")
				.resolve(relativePath);
		return new String(Files.readAllBytes(file), UTF_8);
	}

	private static String moduleSource(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		if (!Files.isDirectory(root.resolve("modules"))) root = root.getParent();
		return new String(Files.readAllBytes(root.resolve("modules").resolve(relativePath)), UTF_8);
	}
}
