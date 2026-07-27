package me.aap.fermata.addon.stremio.security;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;

import me.aap.fermata.provider.FermataContentProvider;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

/** Materializes provider artwork through the bounded, redirect-validating base image provider. */
public final class StremioArtworkLoader {
	private StremioArtworkLoader() {
	}

	public static FutureSupplier<Uri> load(@Nullable String value) {
		String safe = ArtworkUrlSanitizer.sanitize(value);
		if (safe == null) return completedNull();
		if (App.get() == null) return completed(Uri.parse(safe));
		return FermataContentProvider.shareImage(Uri.parse(safe)).ifFail(error -> {
			Log.w("Stremio artwork load failed: ", failureCode(error));
			return null;
		});
	}

	public static FutureSupplier<Bitmap> loadBitmap(@Nullable String value) {
		String safe = ArtworkUrlSanitizer.sanitize(value);
		if ((safe == null) || (App.get() == null)) return completedNull();
		return FermataContentProvider.loadImage(Uri.parse(safe)).ifFail(error -> {
			Log.w("Stremio artwork load failed: ", failureCode(error));
			return null;
		});
	}

	private static String failureCode(Throwable error) {
		Throwable cause = error;
		while ((cause.getCause() != null) && (cause.getCause() != cause)) cause = cause.getCause();
		String type = cause.getClass().getSimpleName();
		return type.isEmpty() ? "unknown" : type;
	}
}
