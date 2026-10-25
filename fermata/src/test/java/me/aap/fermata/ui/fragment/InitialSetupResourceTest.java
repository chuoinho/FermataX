package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class InitialSetupResourceTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String THEMED_TEXT = "?android:attr/textColorPrimary";

	@Test
	public void selectableControlsUseThemedTextColor() throws Exception {
		Document layout = document("initial_setup.xml");
		for (String id : new String[]{"@+id/initial_setup_nav_left",
				"@+id/initial_setup_nav_right", "@+id/initial_setup_voice_enabled"}) {
			assertEquals(THEMED_TEXT, elementById(layout, id)
					.getAttributeNS(ANDROID_NS, "textColor"));
		}
	}

	@Test
	public void spinnerRowsRemainVisibleOnDarkThemes() throws Exception {
		for (String file : new String[]{"initial_setup_spinner_item.xml",
				"initial_setup_spinner_dropdown_item.xml"}) {
			Element row = document(file).getDocumentElement();
			assertEquals("@android:id/text1", row.getAttributeNS(ANDROID_NS, "id"));
			assertEquals(THEMED_TEXT, row.getAttributeNS(ANDROID_NS, "textColor"));
			assertEquals("48dp", row.getAttributeNS(ANDROID_NS, "minHeight"));
		}
	}

	@Test
	public void voiceControlsAreGroupedUnderPermissionsAndDetailsStartHidden() throws Exception {
		Document layout = document("initial_setup.xml");
		Element permissionSection = elementById(layout, "@+id/initial_setup_permission_section");
		Element voiceToggle = elementById(layout, "@+id/initial_setup_voice_enabled");
		Element voiceDetails = elementById(layout, "@+id/initial_setup_voice_details");
		Element voiceLanguage = elementById(layout, "@+id/initial_setup_voice_language");
		Element microphoneTest = elementById(layout, "@+id/initial_setup_microphone_test");
		Element navigationPermissionRows = elementById(layout, "@+id/initial_setup_navigation_permissions");

		assertTrue("voice toggle belongs to the permissions section", isDescendantOf(voiceToggle, permissionSection));
		assertEquals("gone", voiceDetails.getAttributeNS(ANDROID_NS, "visibility"));
		assertTrue(isDescendantOf(voiceLanguage, voiceDetails));
		assertTrue(isDescendantOf(microphoneTest, voiceDetails));
		assertTrue("voice controls appear before navigation access",
				(voiceDetails.compareDocumentPosition(navigationPermissionRows)
						& org.w3c.dom.Node.DOCUMENT_POSITION_FOLLOWING) != 0);
	}

	private static boolean isDescendantOf(org.w3c.dom.Node node, org.w3c.dom.Node ancestor) {
		for (org.w3c.dom.Node parent = node.getParentNode(); parent != null; parent = parent.getParentNode()) {
			if (parent == ancestor) return true;
		}
		return false;
	}

	private static Element elementById(Document document, String id) {
		NodeList elements = document.getElementsByTagName("*");
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			if (id.equals(element.getAttributeNS(ANDROID_NS, "id"))) return element;
		}
		throw new AssertionError("Missing view " + id);
	}

	private static Document document(String file) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resource = root.resolve("src/main/res/layout").resolve(file);
		if (!Files.isRegularFile(resource)) {
			resource = root.resolve("fermata/src/main/res/layout").resolve(file);
		}
		assertTrue("Missing resource " + file, Files.isRegularFile(resource));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(resource.toFile());
	}
}
