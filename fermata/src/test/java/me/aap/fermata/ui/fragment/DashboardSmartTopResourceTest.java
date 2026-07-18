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

public class DashboardSmartTopResourceTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String APP_NS = "http://schemas.android.com/apk/res-auto";

	@Test
	public void actionTargetsAreAtLeast48DpInEveryWidthQualifier() throws Exception {
		assertActionTarget("values/dimens.xml", 48, 48);
		assertActionTarget("values-w700dp/dimens.xml", 48, 48);
		assertActionTarget("values-w1000dp/dimens.xml", 48, 48);
	}

	@Test
	public void compactTwoColumnLayoutFitsFiveActionsFrom460Through557Dp() throws Exception {
		int required = (5 * 48) + (4 * dimenDp("values-w460dp/dimens.xml",
				"dashboard_smart_action_gap"));
		Document layout = document("layout-w460dp/dashboard_smart_top_item.xml");
		float split = Float.parseFloat(elementById(layout, "@+id/dashboard_smart_split")
				.getAttributeNS(APP_NS, "layout_constraintGuide_percent"));
		for (int width : new int[]{460, 557}) {
			int available = Math.round((width - 74 - (2 * 6) - (2 * 4) - (2 * 10)) * split);
			assertTrue("SmartTop actions overflow at " + width + "dp", required <= available);
		}

		Element actions = elementById(layout, "@+id/dashboard_item_actions");
		assertEquals("parent", actions.getAttributeNS(APP_NS,
				"layout_constraintStart_toStartOf"));
		assertEquals("@dimen/dashboard_smart_actions_start_margin",
				actions.getAttributeNS(ANDROID_NS, "layout_marginStart"));
		assertEquals("1.0", actions.getAttributeNS(APP_NS, "layout_constraintVertical_bias"));
	}

	@Test
	public void regularWideLayoutReturnsToBalancedSplitAt558Dp() throws Exception {
		Document layout = document("layout-w558dp/dashboard_smart_top_item.xml");
		assertEquals("0.60", elementById(layout, "@+id/dashboard_smart_split")
				.getAttributeNS(APP_NS, "layout_constraintGuide_percent"));
	}

	@Test
	public void touchExpansionDoesNotIncreaseVisibleSmartTopRowsOrCards() throws Exception {
		assertEquals(176, dimenDp("values/dimens.xml", "dashboard_smart_mobile_height"));
		assertEquals(148, dimenDp("values-w460dp/dimens.xml", "dashboard_smart_height"));

		Document mobile = document("layout/dashboard_smart_top_item.xml");
		assertEquals("wrap_content", elementById(mobile, "@+id/dashboard_recent_items")
				.getAttributeNS(ANDROID_NS, "layout_height"));
		for (String id : new String[]{"@+id/dashboard_recent_item_1",
				"@+id/dashboard_recent_item_2", "@+id/dashboard_recent_item_3"}) {
			assertEquals("28dp", elementById(mobile, id).getAttributeNS(
					ANDROID_NS, "layout_height"));
		}

		Document compact = document("layout-w460dp/dashboard_smart_top_item.xml");
		Element compactRows = elementById(compact, "@+id/dashboard_recent_items");
		assertEquals("0dp", compactRows.getAttributeNS(ANDROID_NS, "layout_height"));
		assertEquals("1", compactRows.getAttributeNS(ANDROID_NS, "layout_weight"));
		for (String id : new String[]{"@+id/dashboard_recent_item_1",
				"@+id/dashboard_recent_item_2", "@+id/dashboard_recent_item_3"}) {
			Element row = elementById(compact, id);
			assertEquals("0dp", row.getAttributeNS(ANDROID_NS, "layout_height"));
			assertEquals("1", row.getAttributeNS(ANDROID_NS, "layout_weight"));
		}
	}

	@Test
	public void compactCardKeepsIconAndActionsInSeparateRows() throws Exception {
		int contentHeight = dimenDp("values-w460dp/dimens.xml", "dashboard_smart_height") -
				(2 * dimenDp("values/dimens.xml", "dashboard_smart_padding"));
		int occupiedHeight = dimenDp("values/dimens.xml", "dashboard_smart_icon_size") +
				dimenDp("values/dimens.xml", "dashboard_smart_action_height");
		assertTrue("SmartTop icon overlaps its action row", contentHeight - occupiedHeight >= 2);
	}

	@Test
	public void largeFontScaleAddsRoomWithoutChangingDefaultCardHeight() {
		assertEquals(0, DashboardFragment.smartTopFontScaleExtraDp(1F));
		assertEquals(12, DashboardFragment.smartTopFontScaleExtraDp(1.3F));
		assertEquals(40, DashboardFragment.smartTopFontScaleExtraDp(2F));
	}

	private static void assertActionTarget(String file, int width, int height) throws Exception {
		assertTrue(dimenDp(file, "dashboard_smart_action_width") >= width);
		assertTrue(dimenDp(file, "dashboard_smart_action_height") >= height);
	}

	private static int dimenDp(String file, String name) throws Exception {
		Document document = document(file);
		NodeList dimensions = document.getElementsByTagName("dimen");
		for (int i = 0; i < dimensions.getLength(); i++) {
			Element dimension = (Element) dimensions.item(i);
			if (!name.equals(dimension.getAttribute("name"))) continue;
			String value = dimension.getTextContent().trim();
			assertTrue("Expected dp value for " + name, value.endsWith("dp"));
			return Integer.parseInt(value.substring(0, value.length() - 2));
		}
		throw new AssertionError("Missing dimension " + name + " in " + file);
	}

	private static Element elementById(Document document, String id) {
		NodeList elements = document.getElementsByTagName("*");
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			if (id.equals(element.getAttributeNS(ANDROID_NS, "id"))) return element;
		}
		throw new AssertionError("Missing view " + id);
	}

	private static Document document(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resource = root.resolve("src/main/res").resolve(relativePath);
		if (!Files.isRegularFile(resource)) {
			resource = root.resolve("fermata/src/main/res").resolve(relativePath);
		}
		assertTrue("Missing resource " + relativePath, Files.isRegularFile(resource));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(resource.toFile());
	}
}
