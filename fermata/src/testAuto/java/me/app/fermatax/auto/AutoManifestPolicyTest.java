package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AutoManifestPolicyTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String TOOLS_NS = "http://schemas.android.com/tools";
	private static final String LEGACY_SERVICE =
			"me.aap.fermata.media.service.FermataMediaService";
	private static final String AUTO_SERVICE =
			"me.app.fermatax.auto.AutoFermataMediaService";

	@Test
	public void autoManifestKeepsPlaybackServiceInternalAndRemovesLegacyEntryPoints()
			throws Exception {
		Document manifest = parse("src/auto/AndroidManifest.xml");
		Element legacy = findNamed(manifest, "service", LEGACY_SERVICE);
		Element internal = findNamed(manifest, "service", AUTO_SERVICE);

		assertNotNull(legacy);
		assertEquals("remove", legacy.getAttributeNS(TOOLS_NS, "node"));
		assertNotNull(internal);
		assertEquals("false", internal.getAttributeNS(ANDROID_NS, "exported"));
		assertTrue(hasNamed(internal, "action", "android.intent.action.MEDIA_BUTTON"));
		assertFalse(hasNamed(internal, "action", "android.media.browse.MediaBrowserService"));
		assertFalse(hasNamed(internal, "action",
				"android.media.action.MEDIA_PLAY_FROM_SEARCH"));
		assertFalse(hasNamed(manifest, "receiver",
				"me.app.fermatax.auto.MediaServiceStarter"));

		Element mainActivity = findNamed(manifest, "activity",
				"me.aap.fermata.ui.activity.MainActivity");
		assertNotNull(mainActivity);
		Element removedSearchFilter = firstChild(mainActivity, "intent-filter");
		assertNotNull(removedSearchFilter);
		assertEquals("remove", removedSearchFilter.getAttributeNS(TOOLS_NS, "node"));
		assertTrue(hasNamed(removedSearchFilter, "action",
				"android.media.action.MEDIA_PLAY_FROM_SEARCH"));
	}

	@Test
	public void automotiveDescriptorDoesNotAdvertiseMediaBrowserCapability() throws Exception {
		Document descriptor = parse("src/auto/res/xml/automotive_app_desc.xml");
		NodeList uses = descriptor.getElementsByTagName("uses");
		Set<String> capabilities = new HashSet<>();
		for (int i = 0; i < uses.getLength(); i++) {
			capabilities.add(((Element) uses.item(i)).getAttribute("name"));
		}

		assertFalse(capabilities.contains("media"));
		assertTrue(capabilities.contains("template"));
		assertTrue(capabilities.contains("service"));
		assertTrue(capabilities.contains("projection"));
	}

	private static Document parse(String path) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new File(path));
	}

	private static boolean hasNamed(Document document, String tag, String name) {
		return findNamed(document, tag, name) != null;
	}

	private static boolean hasNamed(Element parent, String tag, String name) {
		NodeList elements = parent.getElementsByTagName(tag);
		for (int i = 0; i < elements.getLength(); i++) {
			if (name.equals(((Element) elements.item(i)).getAttributeNS(ANDROID_NS, "name"))) {
				return true;
			}
		}
		return false;
	}

	private static Element findNamed(Document document, String tag, String name) {
		NodeList elements = document.getElementsByTagName(tag);
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			if (name.equals(element.getAttributeNS(ANDROID_NS, "name"))) return element;
		}
		return null;
	}

	private static Element firstChild(Element parent, String tag) {
		NodeList elements = parent.getElementsByTagName(tag);
		return (elements.getLength() == 0) ? null : (Element) elements.item(0);
	}
}
