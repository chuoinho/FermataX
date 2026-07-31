package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NavBarMenuContractTest {
	@Test
	public void commonMenuHasRequestedItemsAndOrder() throws Exception {
		String source = source("ui/fragment/NavBarMediator.java");
		String menu = source.substring(source.indexOf("public void showMenu(MainActivityDelegate a)"),
				source.indexOf("public boolean menuItemSelected", source.indexOf(
						"public void showMenu(MainActivityDelegate a)")));

		assertFalse(menu.contains("nav_got_to_current"));
		int settings = menu.indexOf("R.id.settings_fragment");
		int about = menu.indexOf("R.id.nav_about");
		int exit = menu.indexOf("R.id.nav_exit");
		int support = menu.indexOf("R.id.nav_donate");
		assertTrue((settings >= 0) && (settings < about));
		assertTrue((about < exit) && (exit < support));
		assertFalse(menu.contains("isCarActivityNotMirror"));
	}

	@Test
	public void inactiveSelectionEntryIsNotContributed() throws Exception {
		String source = source("ui/fragment/MediaLibFragment.java");
		String menu = source.substring(source.indexOf("public void contributeToNavBarMenu"),
				source.indexOf("public void contributeToContextMenu"));

		assertFalse(menu.contains("addItem(R.id.nav_select,"));
		assertTrue(menu.contains("addItem(R.id.nav_select_all,"));
		assertTrue(menu.contains("addItem(R.id.nav_unselect_all,"));
	}

	@Test
	public void supportLabelNamesTheDeveloper() throws Exception {
		Path file = resource("values/strings.xml");
		NodeList strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(file.toFile()).getElementsByTagName("string");
		for (int i = 0; i < strings.getLength(); i++) {
			Element string = (Element) strings.item(i);
			if (!"donate".equals(string.getAttribute("name"))) continue;
			assertEquals("Support developer", string.getTextContent());
			return;
		}
		throw new AssertionError("Missing donate string");
	}

	private static String source(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata").resolve(relativePath);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata").resolve(relativePath);
		}
		return new String(Files.readAllBytes(file), UTF_8);
	}

	private static Path resource(String relativePath) {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/res").resolve(relativePath);
		return Files.isRegularFile(file) ? file : root.resolve("fermata/src/main/res")
				.resolve(relativePath);
	}
}
