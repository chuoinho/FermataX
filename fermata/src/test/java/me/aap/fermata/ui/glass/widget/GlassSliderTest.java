package me.aap.fermata.ui.glass.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassSliderTest {

	@Test
	public void progressClampsBetweenZeroAndOne() {
		Context context = RuntimeEnvironment.getApplication();
		GlassSlider slider = new GlassSlider(context);

		slider.setProgress(0.75f);
		assertEquals(0.75f, slider.getProgress(), 0.001f);

		slider.setProgress(1.5f);
		assertEquals(1.0f, slider.getProgress(), 0.001f);

		slider.setProgress(-0.5f);
		assertEquals(0.0f, slider.getProgress(), 0.001f);
	}

	@Test
	public void listenerReceivesTrackingAndProgressEvents() {
		Context context = RuntimeEnvironment.getApplication();
		GlassSlider slider = new GlassSlider(context);
		slider.layout(0, 0, 400, 80);

		boolean[] tracking = new boolean[2]; // [start, stop]
		float[] lastProgress = new float[]{-1.0f};

		slider.setOnSliderChangeListener(new GlassSlider.OnSliderChangeListener() {
			@Override
			public void onProgressChanged(GlassSlider s, float progress, boolean fromUser) {
				lastProgress[0] = progress;
			}

			@Override
			public void onStartTrackingTouch(GlassSlider s) {
				tracking[0] = true;
			}

			@Override
			public void onStopTrackingTouch(GlassSlider s) {
				tracking[1] = true;
			}
		});

		// MotionEvent ACTION_DOWN
		MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 200, 40, 0);
		slider.onTouchEvent(down);
		assertTrue(tracking[0]);

		// MotionEvent ACTION_MOVE
		MotionEvent move = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, 300, 40, 0);
		slider.onTouchEvent(move);
		assertTrue(lastProgress[0] > 0.0f);

		// MotionEvent ACTION_UP
		MotionEvent up = MotionEvent.obtain(0, 20, MotionEvent.ACTION_UP, 300, 40, 0);
		slider.onTouchEvent(up);
		assertTrue(tracking[1]);
	}
}
