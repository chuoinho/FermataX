package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LocalizationCoverageTest {
	private static final List<String> LOCALES = List.of("ar", "de", "es", "fr", "hr", "it",
			"ja", "km", "ko", "pl", "pt", "ro", "ru", "tr", "vi", "zh-rTW");
	private static final List<String> RESOURCE_ROOTS = List.of(
			"fermata/src/main/res", "modules/audiobook/src/main/res",
			"modules/chat/src/main/res", "modules/podcast/src/main/res",
			"modules/radio/src/main/res", "modules/stremio/src/main/res",
			"modules/tv/src/main/res", "modules/web/src/main/res",
			"depends/utils/src/main/res");
	private static final Pattern PLACEHOLDER = Pattern.compile("(?<!%)%(?:\\d+\\$)?[a-zA-Z]");

	@Test
	public void everySupportedLocaleCoversAllTranslatableStrings() throws Exception {
		Path repository = repositoryRoot();
		for (String relativeRoot : RESOURCE_ROOTS) {
			Path resourceRoot = repository.resolve(relativeRoot);
			Map<String, String> base = readStrings(resourceRoot.resolve("values"), true);
			for (String locale : LOCALES) {
				Path directory = resourceRoot.resolve("values-" + locale);
				assertTrue("Missing locale directory: " + directory, Files.isDirectory(directory));
				Map<String, String> localized = readStrings(directory, false);
				for (Map.Entry<String, String> entry : base.entrySet()) {
					String value = localized.get(entry.getKey());
					assertNotNull("Missing " + locale + " translation for " + entry.getKey() +
							" in " + relativeRoot, value);
					assertEquals("Placeholder mismatch for " + locale + '/' + entry.getKey() +
							" in " + relativeRoot, placeholders(entry.getValue()), placeholders(value));
					assertTrue("Private-use marker in " + locale + '/' + entry.getKey() +
							" in " + relativeRoot, value.codePoints().noneMatch(
							codePoint -> (codePoint >= 0xE000) && (codePoint <= 0xF8FF)));
				}
			}
		}
	}

	private static Map<String, String> readStrings(Path directory, boolean base) throws Exception {
		Map<String, String> strings = new HashMap<>();
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".xml"))
					.filter(path -> !"voice_cmd.xml".equals(path.getFileName().toString())).toList()) {
				readStrings(file, base, strings);
			}
		}
		return strings;
	}

	private static void readStrings(Path file, boolean base, Map<String, String> strings)
			throws Exception {
		NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(file.toFile()).getDocumentElement().getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (!(node instanceof Element element) || !"string".equals(element.getTagName())) continue;
			if (base && "false".equals(element.getAttribute("translatable"))) continue;
			String name = element.getAttribute("name");
			assertTrue("Duplicate string " + name + " in " + file,
					strings.put(name, element.getTextContent()) == null);
		}
	}

	private static String placeholders(String value) {
		Matcher matcher = PLACEHOLDER.matcher(value);
		List<String> result = new ArrayList<>();
		while (matcher.find()) result.add(matcher.group());
		result.sort(String::compareTo);
		return String.join("|", result);
	}

	private static Path repositoryRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		if (Files.isDirectory(current.resolve("fermata/src/main/res"))) return current;
		Path parent = current.getParent();
		if ((parent != null) && Files.isDirectory(parent.resolve("fermata/src/main/res"))) {
			return parent;
		}
		throw new AssertionError("Unable to locate repository from " + current);
	}
}
