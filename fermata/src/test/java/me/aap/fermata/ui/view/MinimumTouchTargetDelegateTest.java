package me.aap.fermata.ui.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinimumTouchTargetDelegateTest {
	@Test
	public void mobileToolbarTargetExpandsFrom40To48WithoutChangingVisualBounds() {
		int[] visual = {200, 0, 240, 40};
		int[] touch = MinimumTouchTargetDelegate.expandBounds(
				visual[0], visual[1], visual[2], visual[3], 48, 800, 480);

		assertArrayEquals(new int[]{196, 0, 244, 48}, touch);
		assertArrayEquals(new int[]{200, 0, 240, 40}, visual);
	}

	@Test
	public void narrowAaTargetExpandsFrom44To48() {
		assertArrayEquals(new int[]{198, 0, 246, 48},
				MinimumTouchTargetDelegate.expandBounds(200, 0, 244, 44, 48, 800, 480));
	}

	@Test
	public void neighboringToolbarButtonKeepsPriorityInsideItsVisualBounds() {
		int[][] actual = {
				{200, 0, 240, 40},
				{240, 0, 280, 40}
		};
		int[][] expanded = {
				MinimumTouchTargetDelegate.expandBounds(200, 0, 240, 40, 48, 800, 480),
				MinimumTouchTargetDelegate.expandBounds(240, 0, 280, 40, 48, 800, 480)
		};

		assertEquals(0, MinimumTouchTargetDelegate.selectTargetIndex(actual, expanded, 238, 20));
		assertEquals(1, MinimumTouchTargetDelegate.selectTargetIndex(actual, expanded, 242, 20));
	}

	@Test
	public void compactRecentRowsEachExposeA48DpDelegateRegion() {
		int[][] actual = {
				{300, 40, 430, 68},
				{300, 68, 430, 96},
				{300, 96, 430, 124}
		};
		int[][] expanded = new int[actual.length][];
		for (int i = 0; i < actual.length; i++) {
			int[] row = actual[i];
			expanded[i] = MinimumTouchTargetDelegate.expandBounds(
					row[0], row[1], row[2], row[3], 48, 440, 148);
			assertEquals(48, expanded[i][3] - expanded[i][1]);
		}

		assertEquals(0, MinimumTouchTargetDelegate.selectTargetIndex(actual, expanded, 350, 54));
		assertEquals(1, MinimumTouchTargetDelegate.selectTargetIndex(actual, expanded, 350, 82));
		assertEquals(2, MinimumTouchTargetDelegate.selectTargetIndex(actual, expanded, 350, 110));
	}

	@Test
	public void edgeTargetShiftsExpansionInsideParent() {
		assertArrayEquals(new int[]{392, 100, 440, 148},
				MinimumTouchTargetDelegate.expandBounds(412, 120, 440, 148, 48, 440, 148));
	}

	@Test
	public void automotiveActionGapsArePartitionedAt64And76Dp() {
		for (int targetSize : new int[]{64, 76}) {
			int[][] actual = {
					{20, 100, 68, 148},
					{72, 100, 120, 148},
					{124, 100, 172, 148}
			};
			int[][] expanded = new int[actual.length][];
			for (int i = 0; i < actual.length; i++) {
				int[] bounds = actual[i];
				expanded[i] = MinimumTouchTargetDelegate.expandBounds(bounds[0], bounds[1],
						bounds[2], bounds[3], targetSize, 220, 176);
				assertEquals(targetSize, expanded[i][2] - expanded[i][0]);
				assertEquals(targetSize, expanded[i][3] - expanded[i][1]);
			}

			assertEquals(0, MinimumTouchTargetDelegate.selectTargetIndex(
					actual, expanded, 69, 124));
			assertEquals(1, MinimumTouchTargetDelegate.selectTargetIndex(
					actual, expanded, 71, 124));
			assertEquals(2, MinimumTouchTargetDelegate.selectTargetIndex(
					actual, expanded, 150, 124));
		}
	}
}
