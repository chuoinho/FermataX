package me.aap.fermata.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class FermataContentProviderManifestTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String PROVIDER =
			"me.aap.fermata.provider.FermataContentProvider";
	private static final String WRITE_PERMISSION =
			"${applicationId}.permission.WRITE_FERMATA_CONTENT";

	@Test
	public void providerRemainsReadableByAndroidAutoButWriteAccessIsSignatureOnly()
			throws Exception {
		Document manifest = parseManifest();
		Element provider = findNamed(manifest, "provider", PROVIDER);
		Element permission = findNamed(manifest, "permission", WRITE_PERMISSION);

		assertNotNull(provider);
		assertEquals("true", provider.getAttributeNS(ANDROID_NS, "exported"));
		assertEquals("${applicationId}", provider.getAttributeNS(ANDROID_NS, "authorities"));
		assertEquals(WRITE_PERMISSION, provider.getAttributeNS(ANDROID_NS, "writePermission"));
		assertNotNull(permission);
		assertEquals("signature", permission.getAttributeNS(ANDROID_NS, "protectionLevel"));
	}

	private static Document parseManifest() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new File("src/main/AndroidManifest.xml"));
	}

	private static Element findNamed(Document document, String tag, String name) {
		NodeList elements = document.getElementsByTagName(tag);
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			if (name.equals(element.getAttributeNS(ANDROID_NS, "name"))) return element;
		}
		return null;
	}
}
