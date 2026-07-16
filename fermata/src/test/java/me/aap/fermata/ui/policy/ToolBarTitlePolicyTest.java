package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ToolBarTitlePolicyTest {
	@Test
	public void playbackTitleIsLimitedToOwningFragment() {
		assertEquals("Video title", ToolBarTitlePolicy.resolve(10, 10,
				"TV", "Video title"));
		assertEquals("Dashboard", ToolBarTitlePolicy.resolve(20, 10,
				"Dashboard", "Video title"));
		assertEquals("Internet radio", ToolBarTitlePolicy.resolve(30, 10,
				"Internet radio", "Video title"));
	}
}
