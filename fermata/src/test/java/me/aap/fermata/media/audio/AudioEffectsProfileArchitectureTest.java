package me.aap.fermata.media.audio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
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
	public void mediaSessionCallbackUsesTheControllerForExplicitProfileApplication() throws Exception {
		assertFalse(source("media/service/MediaSessionCallback.java")
				.contains("AudioEffectsLegacyApplier.apply"));
		assertTrue(source("media/service/MediaSessionCallback.java")
				.contains("applyAudioEffects"));
	}

	@Test
	public void deferredEqualizerNoticeUsesANonBlockingServiceSafeToast() throws Exception {
		String callback = source("media/service/MediaSessionCallback.java");

		assertTrue(callback.contains("Toast.makeText(getContext(), R.string.equalizer_apply_next_session,"));
		assertFalse(callback.contains("UiUtils.showInfo(getContext(), R.string.equalizer_apply_next_session)"));
	}

	@Test
	public void nativeSessionControllerIsTheOnlyNormalPlaybackAuthority() throws Exception {
		String callback = source("media/service/MediaSessionCallback.java");
		assertTrue(callback.contains("AudioEffectsController"));
		assertFalse(callback.contains("AudioEffectsLegacyApplier.apply"));
		assertTrue(source("media/audio/AudioEffectsController.java").contains("getAudioSessionId()"));
	}

	@Test
	public void legacyAudioEffectsRuntimeAndUiStackAreAbsent() throws Exception {
		assertFalse(mainSourceExists("media/engine/AudioEffects.java"));
		assertFalse(mainSourceExists("media/service/AudioEffectsLegacyApplier.java"));
		assertFalse(mainSourceExists("ui/fragment/AudioEffectsFragment.java"));
		assertFalse(mainSourceExists("ui/view/AudioEffectsView.java"));
		assertFalse(source("media/engine/MediaEngine.java").contains("getAudioEffects()"));
		assertFalse(source("ui/activity/MainActivityDelegate.java")
				.contains("audio_effects_fragment"));
		assertFalse(resourceExists("layout/audio_effects.xml"));
		assertFalse(resourceExists("layout/equalizer_band.xml"));
		assertFalse(resource("values/ids.xml").contains("audio_effects_fragment"));
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
	public void initialOnlyEqualizerCapabilityRequiresASuccessfulDynamicsProcessingBind()
			throws Exception {
		String backend = source("media/audio/NativeSessionAudioEffectsBackend.java");

		assertTrue(backend.contains("if (dynamicsEqualizerInitialized) return " +
				"EqualizerUpdateMode.INITIAL_ONLY;"));
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
							assertTrue(path.toString(), strings.contains("name=\"equalizer\""));
							assertFalse(path.toString(), strings.contains("name=\"equalier\""));
							assertTrue(path.toString(), strings.contains("name=\"preamp\""));
						} catch (Exception error) {
							throw new AssertionError(path.toString(), error);
						}
					});
		}
	}

	@Test
	public void unifiedSettingsExposeTheSharedNativeScreenAndValidatedPreamp()
			throws Exception {
		String builder = source("ui/fragment/AudioEffectsPrefsBuilder.java");
		String screen = source("ui/view/AudioEffectsScreenView.java");

		assertTrue(builder.contains("AudioEffectsScreenView"));
		assertTrue(screen.contains("AudioEffectsProfileRepository.PREAMP_DB"));
		assertTrue(screen.contains("AudioEffectsProfile.MIN_CANONICAL_DB"));
		assertTrue(screen.contains("R.string.preamp"));
		assertTrue(screen.contains("R.string.equalizer"));
		assertFalse(builder.contains("R.string.equalier"));
	}

	@Test
	public void gainControlsAcceptSignedNumericInput() throws Exception {
		String screen = source("ui/view/AudioEffectsScreenView.java");

		assertTrue(screen.contains("InputType.TYPE_NUMBER_FLAG_SIGNED"));
		assertTrue(screen.contains("AudioEffectsProfile.MAX_CANONICAL_DB"));
	}

	@Test
	public void simplifiedScreenUsesMasterOnlyForBandsAndKeepsEffectsOpen() throws Exception {
		String screen = source("ui/view/AudioEffectsScreenView.java");

		assertFalse(screen.contains("equalizerSwitch"));
		assertFalse(screen.contains("additionalToggle"));
		assertFalse(screen.contains("curveMode"));
		assertTrue(screen.contains("band.setEnabled(master)"));
		assertTrue(screen.contains("addGainControl(effectsColumn, R.string.preamp"));
		assertTrue(screen.contains("addSwitchRow(effectsColumn, R.string.bass_boost"));
		assertTrue(screen.contains("addSwitchRow(effectsColumn, R.string.vol_boost"));
		assertTrue(screen.contains("AudioEffectCapability.VIRTUALIZER"));
		assertTrue(screen.contains("getAudioEffectsCapabilities()"));
		assertTrue(screen.contains("virtualizerSwitch = null"));
	}

	@Test
	public void bandTouchArbitrationCanReleaseHorizontalStripScrolling() throws Exception {
		String band = source("ui/view/AudioEffectsBandView.java");

		assertTrue(band.contains("resolveBandGestureAxis"));
		assertTrue(band.contains("disallowParentIntercept(true)"));
		assertTrue(band.contains("disallowParentIntercept(false)"));
	}

	@Test
	public void effectsButtonsUseThemedAppCompatStyling() throws Exception {
		String screen = source("ui/view/AudioEffectsScreenView.java");
		String actions = source("ui/view/AudioEffectsApplyView.java");

		assertTrue(screen.contains("AppCompatButton"));
		assertTrue(actions.contains("AppCompatButton"));
		assertTrue(screen.contains("androidx.appcompat.R.attr.buttonStyle"));
		assertTrue(actions.contains("androidx.appcompat.R.attr.buttonStyle"));
		assertFalse(actions.contains("Color.GRAY"));
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

	private static boolean mainSourceExists(String relativePath) {
		Path root = Path.of(System.getProperty("user.dir"));
		Path main = root.resolve("src/main/java/me/aap/fermata");
		if (!Files.isDirectory(main)) main = root.resolve("fermata/src/main/java/me/aap/fermata");
		return Files.exists(main.resolve(relativePath));
	}

	private static boolean resourceExists(String relativePath) {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resources = root.resolve("src/main/res");
		if (!Files.isDirectory(resources)) resources = root.resolve("fermata/src/main/res");
		return Files.exists(resources.resolve(relativePath));
	}

	private static String resource(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resources = root.resolve("src/main/res");
		if (!Files.isDirectory(resources)) resources = root.resolve("fermata/src/main/res");
		return new String(Files.readAllBytes(resources.resolve(relativePath)), UTF_8);
	}
}
