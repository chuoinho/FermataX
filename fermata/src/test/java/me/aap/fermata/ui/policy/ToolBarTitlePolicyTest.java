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

	@Test
	public void preparationStatusIsLimitedToOwningFragment() {
		assertEquals("Video title | 12 peers | 2 MB/s | 40%",
				ToolBarTitlePolicy.resolve(10, 10, "Stremio", "Video title",
						"12 peers | 2 MB/s | 40%"));
		assertEquals("Dashboard", ToolBarTitlePolicy.resolve(20, 10,
				"Dashboard", "Video title", "12 peers"));
		assertEquals("Video title", ToolBarTitlePolicy.resolve(10, 10,
				"Stremio", "Video title", ""));
	}
}
