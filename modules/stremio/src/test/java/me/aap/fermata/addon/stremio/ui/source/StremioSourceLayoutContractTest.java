package me.aap.fermata.addon.stremio.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class StremioSourceLayoutContractTest {
	private static final String ANDROID = "http://schemas.android.com/apk/res/android";

	@Test
	public void rowAndActionsKeepAaSafeTouchTargets() throws Exception {
		Document row = parse("stremio_source_row.xml");
		assertAtLeastDp(row.getDocumentElement(), "minHeight", 48);
		assertAtLeastDp(element(row, "stremio_source_enabled"), "layout_width", 48);
		assertAtLeastDp(element(row, "stremio_source_enabled"), "layout_height", 48);
		assertAtLeastDp(element(row, "stremio_source_more"), "layout_width", 48);
		assertAtLeastDp(element(row, "stremio_source_more"), "layout_height", 48);
		assertEquals("@string/stremio_source_more_actions",
				attribute(element(row, "stremio_source_more"), "contentDescription"));
		assertAtLeastDp(element(row, "stremio_source_drag_handle"), "layout_width", 48);
		assertAtLeastDp(element(row, "stremio_source_drag_handle"), "layout_height", 48);

		Document list = parse("stremio_source_list.xml");
		assertAtLeastDp(element(list, "stremio_source_add"), "layout_width", 48);
		assertAtLeastDp(element(list, "stremio_source_cancel"), "layout_width", 48);
	}

	@Test
	public void editorDeclaresKeyboardAndPerProviderConsentControls() throws Exception {
		Document form = parse("stremio_source_dialog.xml");
		assertEquals("actionNext", attribute(element(form, "stremio_source_url"), "imeOptions"));
		assertEquals("actionDone", attribute(element(form, "stremio_source_token"), "imeOptions"));
		assertAtLeastDp(element(form, "stremio_source_allow_http"), "minHeight", 48);
		assertAtLeastDp(element(form, "stremio_source_allow_lan"), "minHeight", 48);
		assertNotNull(element(form, "stremio_source_form_error"));
	}

	private static Document parse(String name) throws Exception {
		Path base = Path.of(System.getProperty("user.dir"));
		Path file = base.resolve("src/main/res/layout").resolve(name);
		if (!Files.exists(file)) {
			file = base.resolve("modules/stremio/src/main/res/layout").resolve(name);
		}
		assertTrue("Missing layout " + name, Files.exists(file));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(file.toFile());
	}

	private static Element element(Document document, String id) {
		NodeList nodes = document.getElementsByTagName("*");
		for (int i = 0; i < nodes.getLength(); i++) {
			Element element = (Element) nodes.item(i);
			String value = element.getAttributeNS(ANDROID, "id");
			if (value.endsWith("/" + id)) return element;
		}
		throw new AssertionError("Missing element " + id);
	}

	private static String attribute(Element element, String name) {
		return element.getAttributeNS(ANDROID, name);
	}

	private static void assertAtLeastDp(Element element, String attribute, int minimum) {
		String value = attribute(element, attribute);
		assertTrue(attribute + " must use dp: " + value, value.endsWith("dp"));
		float dp = Float.parseFloat(value.substring(0, value.length() - 2));
		assertTrue(attribute + " must be at least " + minimum + "dp", dp >= minimum);
	}
}
