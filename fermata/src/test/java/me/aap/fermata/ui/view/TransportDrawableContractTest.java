package me.aap.fermata.ui.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Guards the shared, density-safe player transport icon set. */
public class TransportDrawableContractTest {
	private static final String[] DRAWABLES = {
			"prev", "rw", "play", "pause", "stop", "ff", "next"};

	@Test
	public void everyTransportIconUsesTheShared24DpStandardGeometry() throws Exception {
		for (String name : DRAWABLES) {
			String drawable = resource("drawable/" + name + ".xml");
			assertTrue(name, drawable.contains("android:width=\"24dp\""));
			assertTrue(name, drawable.contains("android:height=\"24dp\""));
			assertTrue(name, drawable.contains("android:viewportWidth=\"24\""));
			assertTrue(name, drawable.contains("android:viewportHeight=\"24\""));
		}
		assertTrue(resource("drawable/prev.xml").contains(
				"android:pathData=\"M6,6V18H8V6H6Z\""));
		assertTrue(resource("drawable/next.xml").contains(
				"android:pathData=\"M16,6V18H18V6H16Z\""));
		assertTrue(resource("drawable/play.xml").contains(
				"android:pathData=\"M8,5V19L19,12L8,5Z\""));
		assertTrue(resource("drawable/pause.xml").contains(
				"android:pathData=\"M6,5V19H10V5H6ZM14,5V19H18V5H14Z\""));
		assertTrue(resource("drawable/rw.xml").contains(
				"android:pathData=\"M11,6V18L3,12L11,6Z\""));
		assertTrue(resource("drawable/ff.xml").contains(
				"android:pathData=\"M13,6V18L21,12L13,6Z\""));
		assertTrue(resource("drawable/stop.xml").contains(
				"android:pathData=\"M6,6H18V18H6V6Z\""));
	}

	@Test
	public void phoneCarDashboardAndNotificationsUseOnlyTheSharedTransportSet()
			throws Exception {
		String phone = resource("layout/phone_open_on_car_strip.xml");
		assertTrue(phone.contains("android:src=\"@drawable/prev\""));
		assertTrue(phone.contains("android:src=\"@drawable/play\""));
		assertTrue(phone.contains("android:src=\"@drawable/next\""));
		for (String layout : new String[]{"control_panel_view.xml", "control_panel_view2.xml"}) {
			String panel = resource("layout/" + layout);
			for (String name : new String[]{"prev", "rw", "play_pause", "ff", "next"}) {
				assertTrue(layout + " " + name, panel.contains("@drawable/" + name));
			}
		}
		String service = source("media/service/FermataMediaService.java");
		for (String name : new String[]{"prev", "rw", "play", "pause", "ff", "next"}) {
			assertTrue(name, service.contains("R.drawable." + name));
		}
	}

	private static String source(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve(
				"fermata/src/main/java/me/aap/fermata").resolve(relativePath)), UTF_8);
	}

	private static String resource(String relativePath) throws Exception {
		return new String(Files.readAllBytes(repositoryRoot().resolve("fermata/src/main/res")
				.resolve(relativePath)), UTF_8);
	}

	private static Path repositoryRoot() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
