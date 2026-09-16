package me.aap.fermata.ui.smarttop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Protects metadata artwork precedence without taking ownership of caller-owned bitmaps. */
@RunWith(RobolectricTestRunner.class)
public class SmartTopArtworkResolverTest {
	@Test
	public void directBitmapPrefersAlbumArtThenArtWithoutRecyclingIt() {
		Bitmap album = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
		Bitmap art = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
		MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
				.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, album)
				.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
				.build();

		assertSame(album, SmartTopArtworkResolver.directArtworkBitmap(metadata));
		assertFalse(album.isRecycled());
		assertSame(art, SmartTopArtworkResolver.directArtworkBitmap(
				new MediaMetadataCompat.Builder().putBitmap(
						MediaMetadataCompat.METADATA_KEY_ART, art).build()));
		assertNull(SmartTopArtworkResolver.directArtworkBitmap(null));
	}

	@Test
	public void artworkUriFallsBackToArtUriWhenAlbumArtUriIsAbsentOrBlank() {
		MediaMetadataCompat onlyArt = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, "https://example.test/art.jpg")
				.build();
		MediaMetadataCompat blankAlbum = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "   ")
				.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, "https://example.test/art.jpg")
				.build();

		assertEquals(Uri.parse("https://example.test/art.jpg"),
				SmartTopArtworkResolver.directArtworkUri(onlyArt));
		assertEquals(Uri.parse("https://example.test/art.jpg"),
				SmartTopArtworkResolver.directArtworkUri(blankAlbum));
	}

	@Test
	public void directArtworkBackgroundKeepsTheBitmapWithoutAUri() {
		Bitmap artwork = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);

		SmartTopBackground background = SmartTopBackground.artwork(artwork, "clip-1");

		assertSame(artwork, background.artworkBitmap());
		assertNull(background.artworkUri());
		assertEquals(SmartTopBackground.Kind.ARTWORK, background.kind());
	}

	@Test
	public void directArtworkBackgroundUsesTheSameDecodeBoundsAsTheThumbnail() {
		Bitmap original = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);

		SmartTopBackground background = SmartTopBackground.artwork(original, "clip-large");

		assertTrue(background.artworkBitmap().getWidth() <= 1024);
		assertTrue(background.artworkBitmap().getHeight() <= 1024);
		assertFalse(original.isRecycled());
	}
}
