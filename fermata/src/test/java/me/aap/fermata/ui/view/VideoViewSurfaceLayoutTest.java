package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class VideoViewSurfaceLayoutTest {
	@Test
	public void fixesGravityEvenWhenSurfaceDimensionsAreUnchanged() {
		SurfaceView surface = surface(770, 433, Gravity.FILL);

		VideoView.applySurfaceLayout(surface, 770, 433, false);

		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
		assertEquals(770, params.width);
		assertEquals(433, params.height);
		assertEquals(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, params.gravity);
	}

	@Test
	public void subtitleCanUseVisibleContentInsteadOfPaddedDecoderSurface() {
		SurfaceView subtitle = surface(788, 433, Gravity.FILL);

		VideoView.applySurfaceLayout(subtitle, 770, 433, false);

		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) subtitle.getLayoutParams();
		assertEquals(770, params.width);
		assertEquals(433, params.height);
		assertEquals(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, params.gravity);
	}

	private static SurfaceView surface(int width, int height, int gravity) {
		SurfaceView surface = new SurfaceView(RuntimeEnvironment.getApplication());
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
		params.gravity = gravity;
		surface.setLayoutParams(params);
		return surface;
	}
}
