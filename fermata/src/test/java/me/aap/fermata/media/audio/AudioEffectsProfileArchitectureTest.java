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
						} catch (Exception error) {
							throw new AssertionError(path.toString(), error);
						}
					});
		}
	}

	private static String source(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata").resolve(relativePath);
		if (!Files.isRegularFile(file)) file = root.resolve("fermata/src/main/java/me/aap/fermata")
				.resolve(relativePath);
		return new String(Files.readAllBytes(file), UTF_8);
	}
}
