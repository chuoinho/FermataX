package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PhoneSupportBannerControllerTest {

	@Test
	public void bannerConfigValuesConformToRequirements() {
		assertEquals("https://ko-fi.com/fermatax", PhoneSupportBannerController.DONATE_URL);
		assertTrue("Duration must be between 3s and 5s",
				PhoneSupportBannerController.DISPLAY_DURATION_MS >= 3000L &&
				PhoneSupportBannerController.DISPLAY_DURATION_MS <= 5000L);
		assertTrue(PhoneSupportBannerController.MIN_RESUME_INTERVAL_MS >= 30000L);
	}

	@Test
	public void bannerLayoutDefinesRequiredElements() throws Exception {
		String layout = resource("layout/phone_support_banner.xml");
		assertTrue(layout.contains("android:id=\"@id/phone_support_banner\""));
		assertTrue(layout.contains("android:id=\"@id/phone_support_icon\""));
		assertTrue(layout.contains("android:id=\"@id/phone_support_text\""));
		assertTrue(layout.contains("android:id=\"@id/phone_support_close\""));
		assertTrue(layout.contains("@drawable/phone_support_banner_bg"));
		assertTrue(layout.contains("@drawable/coffee"));
		assertTrue(layout.contains("@drawable/keyboard_close"));
		assertTrue(layout.contains("android:visibility=\"gone\""));
	}

	@Test
	public void bothPhoneMainActivityLayoutsIncludeSupportBanner() throws Exception {
		for (String name : new String[]{"main_activity_left.xml", "main_activity_right.xml"}) {
			String layout = resource("layout/" + name);
			assertTrue(name, layout.contains("layout=\"@layout/phone_support_banner\""));
			assertTrue(name, layout.contains("android:id=\"@id/phone_support_banner\""));
			assertTrue(name, layout.contains("app:layout_constraintBottom_toTopOf=\"@id/phone_bottom_menu\""));
			assertTrue(name, layout.contains("app:layout_constraintStart_toStartOf=\"parent\""));
			assertTrue(name, layout.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
		}
	}

	private static String resource(String file) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve("fermata/src/main/res").resolve(file)),
				StandardCharsets.UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Path.of("").toAbsolutePath();
		while ((current != null) && !Files.isDirectory(current.resolve("fermata/src/main/res"))) {
			current = current.getParent();
		}
		if (current == null) throw new AssertionError("Repository root not found");
		return current;
	}
}
