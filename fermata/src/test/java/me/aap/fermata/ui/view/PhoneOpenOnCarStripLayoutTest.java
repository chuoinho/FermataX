package me.aap.fermata.ui.view;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PhoneOpenOnCarStripLayoutTest {
	@Test public void stripStartsGoneAndBothPhoneShellsFlowBelowIt() throws Exception {
		String strip = resource("phone_open_on_car_strip.xml");
		assertTrue(strip.contains("android:visibility=\"gone\""));
		assertTrue(strip.contains("@+id/phone_open_on_car_toggle"));

		for (String layout : new String[] { "main_activity_left.xml", "main_activity_right.xml" }) {
			String shell = resource(layout);
			assertTrue(shell.contains("layout=\"@layout/phone_open_on_car_strip\""));
			assertTrue(shell.contains("app:layout_constraintTop_toBottomOf=\"@id/phone_open_on_car_strip\""));
		}
	}

	private static String resource(String file) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve("fermata/src/main/res/layout")
				.resolve(file)), StandardCharsets.UTF_8);
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
