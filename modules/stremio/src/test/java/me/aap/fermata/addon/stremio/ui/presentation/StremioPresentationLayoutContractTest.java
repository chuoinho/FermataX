package me.aap.fermata.addon.stremio.ui.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class StremioPresentationLayoutContractTest {
	private static final String ANDROID = "http://schemas.android.com/apk/res/android";
	private static final String APP = "http://schemas.android.com/apk/res-auto";

	@Test
	public void allPresentationModelsHaveNativeLayouts() throws Exception {
		for (String layout : List.of("action", "action_bar", "action_bar_item",
				"section", "poster", "filter",
				"details_header", "episode", "stream_group", "stream_choice", "state_row")) {
			assertTrue("Missing presentation layout " + layout,
					Files.exists(layoutPath("stremio_presentation_" + layout + ".xml")));
		}
	}

	@Test
	public void posterArtworkKeepsTwoByThreeAspectRatio() throws Exception {
		Document poster = parse("stremio_presentation_poster.xml");
		assertEquals("2:3", appAttribute(element(poster,
				"stremio_presentation_poster_image"), "layout_constraintDimensionRatio"));
		Document details = parse("stremio_presentation_details_header.xml");
		assertEquals("2:3", appAttribute(element(details,
				"stremio_presentation_details_poster"), "layout_constraintDimensionRatio"));
	}

	@Test
	public void interactiveSurfacesMeetAaTouchTargets() throws Exception {
		assertAtLeastDp(parse("stremio_presentation_action.xml").getDocumentElement(),
				"minHeight", 48);
		Element actionBarItem = parse("stremio_presentation_action_bar_item.xml")
				.getDocumentElement();
		assertAtLeastDp(actionBarItem, "minWidth", 48);
		assertAtLeastDp(actionBarItem, "minHeight", 48);
		assertAtLeastDp(parse("stremio_presentation_filter_option.xml").getDocumentElement(),
				"layout_height", 48);
		assertAtLeastDp(parse("stremio_presentation_episode.xml").getDocumentElement(),
				"minHeight", 48);
		assertAtLeastDp(parse("stremio_presentation_stream_choice.xml").getDocumentElement(),
				"minHeight", 48);
		assertAtLeastDp(parse("stremio_presentation_state_row.xml").getDocumentElement(),
				"minHeight", 48);

		Document details = parse("stremio_presentation_details_header.xml");
		for (String id : List.of("stremio_presentation_details_watch",
				"stremio_presentation_details_favorite")) {
			assertAtLeastDp(element(details, id), "layout_width", 48);
			assertAtLeastDp(element(details, id), "layout_height", 48);
		}
	}

	@Test
	public void providerIdentityIsConfinedToStreamGrouping() throws Exception {
		for (String layout : List.of("action", "action_bar", "action_bar_item",
				"section", "poster", "filter",
				"details_header", "episode", "stream_choice", "state_row")) {
			String xml = new String(Files.readAllBytes(
					layoutPath("stremio_presentation_" + layout + ".xml")),
					StandardCharsets.UTF_8);
			assertFalse(layout + " must not expose provider-first presentation",
					xml.contains("stream_provider"));
		}
		assertNotNull(element(parse("stremio_presentation_stream_group.xml"),
				"stremio_presentation_stream_provider"));
		Document group = parse("stremio_presentation_stream_group.xml");
		assertNotNull(element(group, "stremio_presentation_stream_state"));
		assertAtLeastDp(element(group, "stremio_presentation_stream_state_progress"),
				"layout_width", 48);
	}

	private static Document parse(String name) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(layoutPath(name).toFile());
	}

	private static Path layoutPath(String name) {
		Path base = Path.of(System.getProperty("user.dir"));
		Path file = base.resolve("src/main/res/layout").resolve(name);
		if (!Files.exists(file)) {
			file = base.resolve("modules/stremio/src/main/res/layout").resolve(name);
		}
		return file;
	}

	private static Element element(Document document, String id) {
		NodeList nodes = document.getElementsByTagName("*");
		for (int i = 0; i < nodes.getLength(); i++) {
			Element element = (Element) nodes.item(i);
			if (element.getAttributeNS(ANDROID, "id").endsWith("/" + id)) return element;
		}
		throw new AssertionError("Missing element " + id);
	}

	private static String appAttribute(Element element, String name) {
		return element.getAttributeNS(APP, name);
	}

	private static void assertAtLeastDp(Element element, String name, int minimum) {
		String value = element.getAttributeNS(ANDROID, name);
		assertTrue(name + " must use dp: " + value, value.endsWith("dp"));
		float dp = Float.parseFloat(value.substring(0, value.length() - 2));
		assertTrue(name + " must be at least " + minimum + "dp", dp >= minimum);
	}
}
