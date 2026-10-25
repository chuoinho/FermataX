package me.aap.fermata.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import me.aap.fermata.ui.activity.MainActivityPrefs.Lang;

/** Acceptance oracle: Turkish, German and Italian are selectable and fully translated. */
public class LanguagesAcceptanceTest {
	private static final String[] RES_DIRS = {
			"fermata/src/main/res", "depends/utils/src/main/res", "modules/audiobook/src/main/res",
			"modules/podcast/src/main/res", "modules/radio/src/main/res", "modules/tv/src/main/res",
			"modules/web/src/main/res"};
	private static final Pattern PLACEHOLDER =
			Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfxXcbeEgGoh%]");
	private static final Pattern RAW_APOSTROPHE = Pattern.compile("(?<!\\\\)'");

	/** name -> texts (one for a string, items for an array, "quantity=text" for plurals). */
	private record Entry(String type, List<String> texts) {}

	@Test public void languageListOffersTheThreeNewLanguagesAfterTheExistingSix() {
		List<Lang> values = Lang.getValues();
		assertEquals(9, values.size());
		assertEquals(List.of(Lang.EN, Lang.VI, Lang.RU, Lang.ES, Lang.PT, Lang.ZH_TW), values.subList(0, 6));
		assertTrue(values.contains(Lang.TR));
		assertTrue(values.contains(Lang.DE));
		assertTrue(values.contains(Lang.IT));
	}

	@Test public void turkishHasExactlyTheEnglishKeys() throws Exception { keyParity("tr"); }
	@Test public void germanHasExactlyTheEnglishKeys() throws Exception { keyParity("de"); }
	@Test public void italianHasExactlyTheEnglishKeys() throws Exception { keyParity("it"); }

	@Test public void turkishKeepsPlaceholdersAndEscaping() throws Exception { wellFormed("tr"); }
	@Test public void germanKeepsPlaceholdersAndEscaping() throws Exception { wellFormed("de"); }
	@Test public void italianKeepsPlaceholdersAndEscaping() throws Exception { wellFormed("it"); }

	@Test public void turkishIsActuallyTranslated() throws Exception { translated("tr"); }
	@Test public void germanIsActuallyTranslated() throws Exception { translated("de"); }
	@Test public void italianIsActuallyTranslated() throws Exception { translated("it"); }

	@Test public void locationPermissionCopyReplacesTheBackgroundRecordingToggleInEveryLanguage() throws Exception {
		String[] locales = {"values", "values-vi", "values-ru", "values-es", "values-pt",
				"values-zh-rTW", "values-tr", "values-de", "values-it"};
		String[] required = {"location_always_title", "location_always_sub", "location_background_disclosure",
				"location_allow_now", "location_skip", "location_state_missing", "location_state_while_using",
				"location_state_always", "location_background_lower_message", "location_open_settings", "location_keep"};
		for (String locale : locales) {
			Map<String, Entry> strings = load(new File(root(), "fermata/src/main/res/" + locale));
			for (String key : required) assertTrue(locale + " missing " + key, strings.containsKey(key));
			for (String key : strings.keySet()) {
				assertTrue(locale + " keeps obsolete background setting " + key,
						!key.startsWith("trip_background_"));
			}
			assertTrue(locale + " keeps obsolete setup description",
					!strings.containsKey("initial_setup_perm_background_sub"));
		}
	}

	@Test public void staleBackgroundLocationMessageIsTranslatedAndTapActionCopyIsRemoved() throws Exception {
		String[] locales = {"values", "values-vi", "values-ru", "values-es", "values-pt",
				"values-zh-rTW", "values-tr", "values-de", "values-it"};
		for (String locale : locales) {
			Map<String, Entry> strings = load(new File(root(), "fermata/src/main/res/" + locale));
			assertTrue(locale + " missing stale background location explanation",
					strings.containsKey("speedo_location_lost_background"));
			assertTrue(locale + " still exposes a tap-to-enable prompt",
					!strings.containsKey("speedo_tap_to_enable"));
		}
	}

	@Test public void readinessLabelsAreTranslatedForEverySupportedLanguage() throws Exception {
		String[] locales = {"values", "values-vi", "values-ru", "values-es", "values-pt",
				"values-zh-rTW", "values-tr", "values-de", "values-it"};
		String[] required = {"readiness_title", "readiness_subtitle", "readiness_accessibility",
				"readiness_connection_disconnected", "readiness_connection_connected",
				"readiness_connection_visible", "readiness_control_available",
				"readiness_control_unavailable", "readiness_nav_notification_access",
				"readiness_group_required", "readiness_group_stability", "readiness_group_optional",
				"readiness_all_ready", "readiness_android_auto_status", "readiness_connection_unknown"};
		Map<String, Entry> english = load(new File(root(), "fermata/src/main/res/values"));
		for (String locale : locales) {
			Map<String, Entry> strings = load(new File(root(), "fermata/src/main/res/" + locale));
			for (String key : required) {
				assertTrue(locale + " missing " + key, strings.containsKey(key));
				if (!locale.equals("values") && !key.equals("readiness_android_auto_status")) {
					assertTrue(locale + " leaves " + key + " in English",
							!strings.get(key).texts().equals(english.get(key).texts()));
				}
			}
		}
	}

	private static void keyParity(String locale) throws Exception {
		for (String res : RES_DIRS) {
			Map<String, Entry> en = load(new File(root(), res + "/values"));
			if (en.isEmpty()) continue;
			File dir = new File(root(), res + "/values-" + locale);
			assertTrue("missing " + dir, dir.isDirectory());
			Map<String, Entry> tr = load(dir);
			List<String> missing = new ArrayList<>(en.keySet());
			missing.removeAll(tr.keySet());
			List<String> extra = new ArrayList<>(tr.keySet());
			extra.removeAll(en.keySet());
			assertTrue(res + " values-" + locale + " missing " + missing, missing.isEmpty());
			assertTrue(res + " values-" + locale + " unknown keys " + extra, extra.isEmpty());
			for (Map.Entry<String, Entry> e : en.entrySet()) {
				Entry t = tr.get(e.getKey());
				assertEquals(res + " " + e.getKey() + " type", e.getValue().type(), t.type());
				if (e.getValue().type().equals("string-array")) {
					assertEquals(res + " " + e.getKey() + " items", e.getValue().texts().size(), t.texts().size());
				}
			}
		}
	}

	private static void wellFormed(String locale) throws Exception {
		for (String res : RES_DIRS) {
			Map<String, Entry> en = load(new File(root(), res + "/values"));
			if (en.isEmpty()) continue;
			Map<String, Entry> tr = load(new File(root(), res + "/values-" + locale));
			for (Map.Entry<String, Entry> e : en.entrySet()) {
				Entry t = tr.get(e.getKey());
				if (t == null) continue; // reported by the key parity test
				String where = res + " values-" + locale + " " + e.getKey();
				List<String> enTexts = e.getValue().texts(), trTexts = t.texts();
				if (e.getValue().type().equals("plurals")) {
					// Plural quantities differ by language; compare against English "other".
					String other = value(enTexts, "other");
					for (String item : trTexts) checkText(where, other, item.substring(item.indexOf('=') + 1));
				} else {
					for (int i = 0; i < Math.min(enTexts.size(), trTexts.size()); i++) {
						checkText(where, enTexts.get(i), trTexts.get(i));
					}
				}
			}
		}
	}

	private static void checkText(String where, String en, String tr) {
		assertTrue(where + " is empty", en.trim().isEmpty() || !tr.trim().isEmpty());
		assertEquals(where + " placeholders", placeholders(en), placeholders(tr));
		boolean quoted = tr.length() >= 2 && tr.startsWith("\"") && tr.endsWith("\"");
		assertTrue(where + " has an unescaped apostrophe: " + tr, quoted || !RAW_APOSTROPHE.matcher(tr).find());
	}

	private static void translated(String locale) throws Exception {
		int total = 0, same = 0;
		for (String res : RES_DIRS) {
			Map<String, Entry> en = load(new File(root(), res + "/values"));
			if (en.isEmpty()) continue;
			Map<String, Entry> tr = load(new File(root(), res + "/values-" + locale));
			for (Map.Entry<String, Entry> e : en.entrySet()) {
				if (!e.getValue().type().equals("string")) continue;
				Entry t = tr.get(e.getKey());
				total++;
				if (t == null || t.texts().get(0).trim().equals(e.getValue().texts().get(0).trim())) same++;
			}
		}
		assertTrue("no strings found", total > 0);
		double translatedShare = (total - same) / (double) total;
		assertTrue("values-" + locale + " only " + Math.round(translatedShare * 100) + "% translated",
				translatedShare >= 0.60);
	}

	private static List<String> placeholders(String s) {
		List<String> found = new ArrayList<>();
		Matcher m = PLACEHOLDER.matcher(s);
		while (m.find()) found.add(m.group());
		Collections.sort(found);
		return found;
	}

	private static String value(List<String> items, String quantity) {
		for (String i : items) if (i.startsWith(quantity + "=")) return i.substring(quantity.length() + 1);
		return items.isEmpty() ? "" : items.get(items.size() - 1).substring(items.get(items.size() - 1).indexOf('=') + 1);
	}

	private static Map<String, Entry> load(File dir) throws Exception {
		Map<String, Entry> map = new LinkedHashMap<>();
		File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
		if (files == null) return map;
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		for (File file : files) {
			Document doc = f.newDocumentBuilder().parse(file); // throws on malformed XML
			NodeList children = doc.getDocumentElement().getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (!(children.item(i) instanceof Element el)) continue;
				String type = el.getTagName();
				if (!type.equals("string") && !type.equals("plurals") && !type.equals("string-array")) continue;
				if ("false".equals(el.getAttribute("translatable"))) continue;
				List<String> texts = new ArrayList<>();
				if (type.equals("string")) {
					texts.add(el.getTextContent());
				} else {
					NodeList items = el.getElementsByTagName("item");
					for (int j = 0; j < items.getLength(); j++) {
						Element item = (Element) items.item(j);
						texts.add(type.equals("plurals") ? item.getAttribute("quantity") + "=" + item.getTextContent()
								: item.getTextContent());
					}
				}
				map.put(el.getAttribute("name"), new Entry(type, texts));
			}
		}
		return map;
	}

	private static File root() {
		File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
		while (dir != null && !new File(dir, "settings.gradle").isFile()) dir = dir.getParentFile();
		if (dir == null) throw new IllegalStateException("repository root not found");
		return dir;
	}

	@SuppressWarnings("unused")
	private static boolean isElement(Node n) { return n instanceof Element; }
}
