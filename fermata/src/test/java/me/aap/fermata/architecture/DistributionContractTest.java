package me.aap.fermata.architecture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the non-negotiable single-package FermataX distribution contract. */
public class DistributionContractTest {
	@Test
	public void localBuildProducesOneUniversalApkWithoutPlatformOrAbiSplit() throws Exception {
		String build = read("build.sh");
		assertTrue(build.contains("BUILD_TYPE='Release'"));
		assertTrue(build.contains("BUILD_TYPE='Debug'"));
		assertTrue(build.contains("package${app_flavor}Auto${BUILD_TYPE}UniversalApk"));
		assertTrue(build.contains("verifyWebOnlyProductionGraph"));
		assertTrue(build.contains("output_root=\"fermata/build/outputs/apk_from_bundle\""));
		assertTrue(build.contains("Expected exactly one FermataX $ext artifact"));
		assertTrue(build.contains("jar tf \"$path\""));
		assertTrue(build.contains("FermataX-${version}.$ext"));
		assertTrue(build.contains("cp \"$path\" \"$dst\""));
		assertFalse(build.contains("-PABI="));
		assertFalse(build.contains("package${app_flavor}Mobile"));
		assertFalse(build.contains("fermata-auto-"));
		assertFalse(build.contains("fermata-mobile-"));
	}

	@Test
	public void ciPublishesExactlyOneNeutralUniversalApkPath() throws Exception {
		String ci = read(".github/workflows/ci.yml");
		assertTrue(ci.contains("Verify Web-only production graph"));
		assertTrue(ci.contains("./build.sh -d"));
		assertTrue(ci.contains("dist/FermataX-debug-universal.apk"));
		assertTrue(ci.contains("Upload single FermataX universal APK"));
		assertFalse(ci.contains("./gradlew :fermata:packageAutoDebugUniversalApk"));
		assertFalse(ci.contains(":fermata:packageMobileDebugUniversalApk"));
		assertFalse(ci.contains("FermataX-mobile"));
		assertFalse(ci.contains("FermataX-auto"));
	}

	private static String read(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("settings.gradle")) &&
					Files.isDirectory(current.resolve("fermata"))) return current;
			current = current.getParent();
		}
		throw new AssertionError("Unable to locate repository root");
	}
}
