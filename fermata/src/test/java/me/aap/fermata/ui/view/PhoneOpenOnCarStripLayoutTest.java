package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveConnectionState.State;

public class PhoneOpenOnCarStripLayoutTest {
	@Test public void headerStartsGoneAndBothPhoneShellsFlowBelowDivider() throws Exception {
		String strip = resource("phone_open_on_car_strip.xml");
		assertTrue(strip.contains("android:visibility=\"gone\""));
		assertTrue(strip.contains("@+id/phone_open_on_car_toggle"));
		assertTrue(strip.contains("@+id/phone_header_aa_status"));
		assertTrue(strip.contains("@+id/phone_header_aa_dot"));
		assertTrue(strip.contains("@+id/phone_header_utility"));
		assertTrue(strip.contains("@+id/phone_header_previous"));
		assertTrue(strip.contains("@+id/phone_header_play_pause"));
		assertTrue(strip.contains("@+id/phone_header_next"));
		assertTrue(strip.contains("@+id/phone_header_progress"));
		assertTrue(strip.contains("@+id/phone_header_elapsed"));
		assertTrue(strip.contains("@+id/phone_header_duration"));
		assertTrue(strip.contains("style=\"?attr/appSeekBarStyle\""));
		assertTrue(strip.contains("app:layout_constraintStart_toStartOf=\"parent\""));
		assertTrue(strip.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
		assertTrue(strip.contains("android:layout_height=\"228dp\""));

		for (String layout : new String[] { "main_activity_left.xml", "main_activity_right.xml" }) {
			String shell = resource(layout);
			assertTrue(shell.contains("layout=\"@layout/phone_open_on_car_strip\""));
			assertTrue(shell.contains("app:layout_constraintTop_toBottomOf=\"@id/phone_header_divider\""));
		}
	}

	@Test public void phoneHeaderOwnsTheFullTopRowBeforeTheNavigationRail() throws Exception {
		for (String layout : new String[] { "main_activity_left.xml", "main_activity_right.xml" }) {
			String header = element(resource(layout), "phone_open_on_car_strip");
			String navBar = element(resource(layout), "nav_bar");

			assertTrue(header.contains("app:layout_constraintStart_toStartOf=\"parent\""));
			assertTrue(header.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
			assertFalse(header.contains("@id/nav_bar"));
			assertTrue(navBar.contains(
					"app:layout_constraintTop_toBottomOf=\"@id/phone_header_divider\""));
		}
	}

	@Test public void phoneHeaderDividerSeparatesHeaderFromPhoneContent() throws Exception {
		for (String layout : new String[] { "main_activity_left.xml", "main_activity_right.xml" }) {
			String shell = resource(layout);
			String divider = element(shell, "phone_header_divider");

			assertTrue(divider.contains("android:layout_width=\"0dp\""));
			assertTrue(divider.contains("android:layout_height=\"1dp\""));
			assertTrue(divider.contains("android:background=\"@drawable/phone_header_divider\""));
			assertTrue(divider.contains("app:layout_constraintStart_toStartOf=\"parent\""));
			assertTrue(divider.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
			assertTrue(divider.contains("app:layout_constraintTop_toBottomOf=\"@id/phone_open_on_car_strip\""));
			String navBar = element(shell, "nav_bar");
			String toolBar = element(shell, "tool_bar");
			assertTrue(navBar.contains("app:layout_constraintTop_toBottomOf=\"@id/phone_header_divider\""));
			assertTrue(toolBar.contains("app:layout_constraintTop_toBottomOf=\"@id/phone_header_divider\""));
		}
	}

	@Test public void headerUsesExactlyTwoCompactAaLabelsAndShortOpenOnCarTitle()
			throws Exception {
		String strings = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res/values/control_strings.xml")), StandardCharsets.UTF_8);
		assertTrue(strings.contains(
				"name=\"phone_header_aa_disconnect\" translatable=\"false\">AA disconnect"));
		assertTrue(strings.contains(
				"name=\"phone_header_aa_connected\" translatable=\"false\">AA connected"));
		assertTrue(strings.contains(
				"name=\"phone_open_on_car_strip_title\" translatable=\"false\">Open on car"));
		assertEquals(2, count(strings, "name=\"phone_header_aa_"));
		String vietnameseStrings = new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/res/values-vi/control_strings.xml")), StandardCharsets.UTF_8);
		assertTrue(vietnameseStrings.contains(
				"name=\"phone_header_aa_disconnect\">AA disconnect"));
		assertTrue(vietnameseStrings.contains(
				"name=\"phone_header_aa_connected\">AA connected"));
	}

	@Test public void headerMapsEveryConnectedAutomotiveStateToTheSameCompactLabel() {
		assertEquals(R.string.phone_header_aa_disconnect,
				PhonePlaybackCarHeaderController.aaStatus(State.DISCONNECTED));
		assertEquals(R.string.phone_header_aa_connected,
				PhonePlaybackCarHeaderController.aaStatus(State.CONNECTED));
		assertEquals(R.string.phone_header_aa_connected,
				PhonePlaybackCarHeaderController.aaStatus(State.APP_VISIBLE));
	}

	private static String resource(String file) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve("fermata/src/main/res/layout")
				.resolve(file)), StandardCharsets.UTF_8);
	}

	private static String element(String xml, String id) {
		String marker = "android:id=\"@+id/" + id + "\"";
		int from = xml.indexOf(marker);
		if (from < 0) throw new AssertionError("Missing element: " + id);
		int start = xml.lastIndexOf('<', from);
		int end = xml.indexOf("/>", from);
		if (end < 0) throw new AssertionError("Missing element end: " + id);
		return xml.substring(start, end + 2);
	}

	private static Path repositoryRoot() {
		Path current = Path.of("").toAbsolutePath();
		while ((current != null) && !Files.isDirectory(current.resolve("fermata/src/main/res"))) {
			current = current.getParent();
		}
		if (current == null) throw new AssertionError("Repository root not found");
		return current;
	}

	private static int count(String text, String token) {
		int count = 0;
		for (int from = text.indexOf(token); from >= 0; from = text.indexOf(token, from + 1)) count++;
		return count;
	}
}
