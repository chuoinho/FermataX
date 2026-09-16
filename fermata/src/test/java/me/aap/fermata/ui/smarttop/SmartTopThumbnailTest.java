package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Ensures the fixed card image slot accepts real portrait/landscape art but not icon-sized art. */
@RunWith(RobolectricTestRunner.class)
public class SmartTopThumbnailTest {
	@Test
	public void acceptsPortraitAndLandscapeArtworkButRejectsAnIcon() {
		Bitmap portrait = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
		Bitmap landscape = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888);
		Bitmap icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);

		assertTrue(SmartTopThumbnail.fromBitmap("portrait", portrait).bitmap().getHeight() <= 1024);
		assertTrue(SmartTopThumbnail.fromBitmap("landscape", landscape).bitmap().getWidth() <= 1024);
		assertNull(SmartTopThumbnail.fromBitmap("icon", icon));
	}

	@Test
	public void boundsDecodedThumbnailWithoutMutatingTheCallerBitmap() {
		Bitmap original = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);

		Bitmap normalized = SmartTopThumbnail.fromBitmap("large", original).bitmap();

		assertTrue(normalized.getWidth() <= 1024);
		assertTrue(normalized.getHeight() <= 1024);
		assertTrue(((long) normalized.getWidth() * normalized.getHeight() * 4L) <= 4L * 1024L * 1024L);
		assertTrue(!original.isRecycled());
	}

	@Test
	public void sourceArtworkDefersNormalizationUntilTheBackgroundPipeline() {
		Bitmap original = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);

		SmartTopThumbnail source = SmartTopThumbnail.fromSource("large", original);
		SmartTopBackground background = SmartTopBackground.artworkSource(original, "large");

		assertSame(original, source.bitmap());
		assertSame(original, background.artworkBitmap());
	}
}
