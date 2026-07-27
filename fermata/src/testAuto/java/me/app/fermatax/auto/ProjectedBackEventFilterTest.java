package me.app.fermatax.auto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectedBackEventFilterTest {
	@Test
	public void suppressesImmediateSyntheticDuplicateAfterRealBack() {
		ProjectedBackEventFilter filter = new ProjectedBackEventFilter();

		assertFalse(filter.shouldSuppress(true, 1000L, 0, 1000L));
		assertTrue(filter.shouldSuppress(true, 0L, -1, 1010L));
	}

	@Test
	public void preservesStandaloneAndLateSyntheticBack() {
		ProjectedBackEventFilter filter = new ProjectedBackEventFilter();

		assertFalse(filter.shouldSuppress(true, 0L, -1, 1000L));
		assertFalse(filter.shouldSuppress(true, 2000L, 0, 2000L));
		assertFalse(filter.shouldSuppress(true, 0L, -1,
				2000L + ProjectedBackEventFilter.DUPLICATE_WINDOW_MILLIS + 1L));
	}

	@Test
	public void nonBackEventsNeverArmOrTriggerFilter() {
		ProjectedBackEventFilter filter = new ProjectedBackEventFilter();

		assertFalse(filter.shouldSuppress(false, 1000L, 0, 1000L));
		assertFalse(filter.shouldSuppress(true, 0L, -1, 1001L));
		assertFalse(filter.shouldSuppress(false, 0L, -1, 1002L));
	}

	@Test
	public void acceptsDuplicateAtWindowBoundary() {
		ProjectedBackEventFilter filter = new ProjectedBackEventFilter();

		assertFalse(filter.shouldSuppress(true, 1000L, 0, 1000L));
		assertTrue(filter.shouldSuppress(true, 0L, -1,
				1000L + ProjectedBackEventFilter.DUPLICATE_WINDOW_MILLIS));
	}
}
