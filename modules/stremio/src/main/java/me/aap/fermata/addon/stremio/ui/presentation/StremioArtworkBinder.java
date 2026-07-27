package me.aap.fermata.addon.stremio.ui.presentation;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.stremio.R;
import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;
import me.aap.fermata.addon.stremio.security.StremioArtworkLoader;
import me.aap.utils.async.FutureSupplier;

/** Recycle-safe bridge to the existing bounded Stremio artwork loader. */
public class StremioArtworkBinder {
	private static final int PLACEHOLDER_BACKGROUND = Color.rgb(18, 27, 42);

	public void bind(@NonNull ImageView view, String artwork, @DrawableRes int placeholder) {
		bind(view, artwork, null, placeholder);
	}

	public void bind(@NonNull ImageView view, String artwork, String fallbackArtwork,
			@DrawableRes int placeholder) {
		clear(view);
		view.setBackgroundColor(PLACEHOLDER_BACKGROUND);
		view.setScaleType(ImageView.ScaleType.CENTER);
		view.setImageResource(placeholder);
		loadCandidate(view, artworkCandidates(artwork, fallbackArtwork), 0);
	}

	private void loadCandidate(@NonNull ImageView view, List<String> candidates, int index) {
		if (index >= candidates.size()) return;

		FutureSupplier<Bitmap> request = StremioArtworkLoader.loadBitmap(candidates.get(index)).main();
		view.setTag(R.id.stremio_presentation_artwork_request, request);
		request.onCompletion((bitmap, error) -> {
			if (view.getTag(R.id.stremio_presentation_artwork_request) != request) return;
			view.setTag(R.id.stremio_presentation_artwork_request, null);
			if ((error == null) && (bitmap != null)) {
				view.setBackgroundColor(Color.TRANSPARENT);
				view.setScaleType(ImageView.ScaleType.CENTER_CROP);
				view.setImageBitmap(bitmap);
			}
			else loadCandidate(view, candidates, index + 1);
		});
	}

	static List<String> artworkCandidates(String artwork, String fallbackArtwork) {
		List<String> result = new ArrayList<>(2);
		String primary = ArtworkUrlSanitizer.sanitize(artwork);
		String fallback = ArtworkUrlSanitizer.sanitize(fallbackArtwork);
		if (primary != null) result.add(primary);
		if ((fallback != null) && !fallback.equals(primary)) result.add(fallback);
		return List.copyOf(result);
	}

	public void clear(@NonNull ImageView view) {
		Object request = view.getTag(R.id.stremio_presentation_artwork_request);
		view.setTag(R.id.stremio_presentation_artwork_request, null);
		if (request instanceof FutureSupplier<?> future) future.cancel(true);
		view.setBackgroundColor(Color.TRANSPARENT);
		view.setScaleType(ImageView.ScaleType.CENTER_CROP);
		view.setImageDrawable(null);
	}
}
