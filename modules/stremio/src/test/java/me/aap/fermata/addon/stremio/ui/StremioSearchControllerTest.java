package me.aap.fermata.addon.stremio.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class StremioSearchControllerTest {
	@Test
	public void pendingQueryIsTrimmedAndConsumedOnce() {
		StremioSearchController controller = new StremioSearchController();
		controller.showResults("  Movie  ", false, query -> { throw new AssertionError(); });

		assertEquals("Movie", controller.takePendingQuery());
		assertNull(controller.takePendingQuery());
	}

	@Test
	public void readyQueryNavigatesWithoutChangingItsText() {
		StremioSearchController controller = new StremioSearchController();
		AtomicReference<String> result = new AtomicReference<>();
		controller.showResults("  Series  ", true, result::set);

		assertEquals("Series", result.get());
	}
}
