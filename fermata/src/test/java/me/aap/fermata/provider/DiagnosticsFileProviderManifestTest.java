package me.aap.fermata.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DiagnosticsFileProviderManifestTest {
	private static final String ANDROID = "http://schemas.android.com/apk/res/android";

	@Test
	public void diagnosticsProviderIsPrivateAndNarrowlyScoped() throws Exception {
		Path root = repositoryRoot();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Element manifest = factory.newDocumentBuilder()
				.parse(root.resolve("fermata/src/main/AndroidManifest.xml").toFile())
				.getDocumentElement();
		Element provider = findProvider(manifest, "me.aap.fermata.provider.DiagnosticsFileProvider",
				"${applicationId}.DiagnosticsFileProvider");

		assertNotNull(provider);
		assertEquals("false", provider.getAttributeNS(ANDROID, "exported"));
		assertEquals("true", provider.getAttributeNS(ANDROID, "grantUriPermissions"));

		Element paths = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(root.resolve("fermata/src/main/res/xml/diagnostics_file_paths.xml").toFile())
				.getDocumentElement();
		NodeList children = paths.getChildNodes();
		int elements = 0;
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (!(node instanceof Element path)) continue;
			elements++;
			assertEquals("cache-path", path.getTagName());
			assertEquals("diagnostics/reports/", path.getAttribute("path"));
			assertFalse(path.getAttribute("path").contains(".."));
		}
		assertEquals(1, elements);
	}

	private static Element findProvider(Element manifest, String name, String authority) {
		NodeList providers = manifest.getElementsByTagName("provider");
		for (int i = 0; i < providers.getLength(); i++) {
			Element provider = (Element) providers.item(i);
			if (name.equals(provider.getAttributeNS(ANDROID, "name")) &&
					authority.equals(provider.getAttributeNS(ANDROID, "authorities"))) {
				return provider;
			}
		}
		return null;
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main"))) return parent;
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
