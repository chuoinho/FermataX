# SmartTopCard background copy-paste snippets

These snippets are implementation scaffolds written specifically for FermataX. They are not copied
verbatim from a third-party repository. Copy them into the named production files, then reconcile
imports, naming and constructor call sites against the current branch before compiling.

The snippets intentionally omit provider expansion, live FFT, animation, network loading and changes
to foreground geometry.

## 1. `SmartTopBackground.java`

Target: `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopBackground.java`

```java
package me.aap.fermata.ui.smarttop;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Immutable description of the pixels behind SmartTopCard foreground content. */
public record SmartTopBackground(
		Kind kind,
		@Nullable Uri artworkUri,
		String identity) {
	public SmartTopBackground {
		Objects.requireNonNull(kind, "kind");
		identity = Objects.requireNonNull(identity, "identity");
		if (identity.isBlank()) throw new IllegalArgumentException("Background identity is blank");
		if ((kind == Kind.ARTWORK) != (artworkUri != null)) {
			throw new IllegalArgumentException("Only artwork backgrounds carry a URI");
		}
	}

	public static SmartTopBackground artwork(Uri uri, String itemIdentity) {
		Objects.requireNonNull(uri, "uri");
		String uriToken = Integer.toHexString(uri.toString().hashCode());
		return new SmartTopBackground(Kind.ARTWORK, uri,
				"art:" + Objects.requireNonNull(itemIdentity, "itemIdentity") + ':' + uriToken);
	}

	public static SmartTopBackground audioSpectrum(String sourceIdentity) {
		return new SmartTopBackground(Kind.AUDIO_SPECTRUM, null,
				"audio:" + Objects.requireNonNull(sourceIdentity, "sourceIdentity"));
	}

	public static SmartTopBackground sourceFallback(String sourceIdentity) {
		return new SmartTopBackground(Kind.SOURCE_FALLBACK, null,
				"source:" + Objects.requireNonNull(sourceIdentity, "sourceIdentity"));
	}

	public static SmartTopBackground empty() {
		return new SmartTopBackground(Kind.EMPTY, null, "empty");
	}

	public enum Kind {
		ARTWORK,
		AUDIO_SPECTRUM,
		SOURCE_FALLBACK,
		EMPTY
	}
}
```

Do not derive `identity` from a raw URL in logs or UI. Its only purpose is stale-bind comparison.
If the branch already has a safe hashing helper, replace `String.hashCode()` with that helper.

## 2. Pure selection and dimension policy

Target: `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopBackgroundPolicy.java`

```java
package me.aap.fermata.ui.smarttop;

/** Pure SmartTop background selection rules; no Android view, network or addon lookup. */
public final class SmartTopBackgroundPolicy {
	public static final int MIN_SHORT_EDGE_PX = 256;
	public static final double MIN_ASPECT = 0.8D;
	public static final double MAX_ASPECT = 1.25D;

	private SmartTopBackgroundPolicy() {
	}

	public static SmartTopBackground.Kind select(boolean empty,
			boolean eligibleArtwork, boolean provenAudioAddon) {
		if (empty) return SmartTopBackground.Kind.EMPTY;
		if (eligibleArtwork) return SmartTopBackground.Kind.ARTWORK;
		if (provenAudioAddon) return SmartTopBackground.Kind.AUDIO_SPECTRUM;
		return SmartTopBackground.Kind.SOURCE_FALLBACK;
	}

	public static boolean eligibleDimensions(int width, int height, boolean animated) {
		if (animated || (width <= 0) || (height <= 0)) return false;
		if (Math.min(width, height) < MIN_SHORT_EDGE_PX) return false;
		double aspect = (double) width / (double) height;
		return (aspect >= MIN_ASPECT) && (aspect <= MAX_ASPECT);
	}
}
```

Suggested tests:

```java
@Test
public void artworkDimensionBoundariesAreInclusive() {
	assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(255, 255, false));
	assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(256, 256, false));
	assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(256, 320, false)); // 0.8
	assertTrue(SmartTopBackgroundPolicy.eligibleDimensions(320, 256, false)); // 1.25
	assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(455, 256, false)); // near 16:9
	assertFalse(SmartTopBackgroundPolicy.eligibleDimensions(512, 512, true));
}
```

## 3. Direct artwork provenance

Do not call `PlayableItem.getIconUri()`. Extract only the direct metadata field:

```java
@Nullable
static Uri directArtworkUri(MediaMetadataCompat metadata) {
	if (metadata == null) return null;
	String value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
	if ((value == null) || value.isBlank()) return null;
	Uri uri = Uri.parse(value);
	return (uri.getScheme() == null) ? null : uri;
}
```

Before accepting a `content` URI, allow only trusted local authorities used by Fermata/MediaStore.
Do not assume every arbitrary `ContentProvider` is local or side-effect free.

## 4. Cache/local-only bitmap entry point

Target: add inside `BitmapCache`. This scaffold deliberately reuses private cache layout helpers so
there is one owner for memory keys and disk paths.

```java
@NonNull
public FutureSupplier<Bitmap> getBitmapIfCached(Context ctx, String uri, boolean resize) {
	if ((uri == null) || uri.isBlank()) return completedNull();
	String orig = FermataContentProvider.getOrigUri(uri);
	String normalized = (orig == null) ? uri : orig;
	Uri parsed = Uri.parse(normalized);
	String scheme = parsed.getScheme();
	if (scheme == null) return completedNull();

	int size = resize ? getIconSize(ctx) : 0;
	String iconUri = resize ? toIconUri(normalized, size) : null;
	String memoryKey = resize ? iconUri : normalized;
	Bitmap memory = getCachedBitmap(memoryKey);
	if (memory != null) return completed(memory);

	if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
		File iconFile = resize ? new File(iconsCache,
				iconUri.substring(iconsCacheUri.length())) : null;
		if ((iconFile != null) && iconFile.isFile()) {
			return queue.enqueue(() -> loadBitmap(ctx, iconUri, memoryKey, 0));
		}

		File originalFile = toImageFile(normalized);
		if (!originalFile.isFile()) return completedNull();
		String localUri = Uri.fromFile(originalFile).toString();
		return queue.enqueue(() -> {
			Bitmap bitmap = loadBitmap(ctx, localUri, memoryKey, size);
			if (resize && (bitmap != null) && (iconFile != null) && !iconFile.isFile()) {
				saveIcon(bitmap, iconFile);
			}
			return bitmap;
		});
	}

	boolean local = ContentResolver.SCHEME_FILE.equals(scheme) ||
			ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme) ||
			ContentResolver.SCHEME_CONTENT.equals(scheme);
	if (!local) return completedNull();

	return queue.enqueue(() -> loadBitmap(ctx, normalized, iconUri, true, size));
}
```

Required review before committing this method:

- Confirm the HTTP disk filename is still owned by `toImageFile()`.
- Confirm no call path reaches `loadHttpBitmap()`, `downloadImage()`, `URL.openStream()` or the VFS
  default branch.
- Add a transport-spy regression test for a cold HTTP(S) miss.
- Add an authority allowlist before passing arbitrary `content` URIs from SmartTop.
- Decode and dimension validation stay off the main thread.

## 5. Full-card background drawable

Target: `fermata/src/main/java/me/aap/fermata/ui/smarttop/SmartTopCardBackgroundDrawable.java`

This is a compact rendering scaffold. It draws against the root background bounds, so the existing
adaptive content padding does not inset the artwork.

```java
package me.aap.fermata.ui.smarttop;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

final class SmartTopCardBackgroundDrawable extends Drawable {
	private static final float[] SPECTRUM =
			{0.20F, 0.46F, 0.74F, 0.38F, 0.88F, 0.58F, 0.30F, 0.68F, 0.42F};
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Rect source = new Rect();
	private final RectF target = new RectF();
	private final float radiusPx;
	private final SmartTopBackground.Kind kind;
	@Nullable
	private Bitmap artwork;

	SmartTopCardBackgroundDrawable(float radiusPx, SmartTopBackground.Kind kind) {
		this.radiusPx = radiusPx;
		this.kind = kind;
	}

	void setArtwork(@Nullable Bitmap bitmap) {
		artwork = bitmap;
		invalidateSelf();
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		if (bounds.isEmpty()) return;
		SmartTopBackground.Kind renderedKind =
				((kind == SmartTopBackground.Kind.ARTWORK) && (artwork == null)) ?
						SmartTopBackground.Kind.SOURCE_FALLBACK : kind;
		int save = canvas.save();
		target.set(bounds);
		canvas.clipRoundRect(target, radiusPx, radiusPx);

		paint.setShader(new LinearGradient(bounds.left, 0F, bounds.right, 0F,
				Color.rgb(20, 31, 45), Color.rgb(8, 11, 17), Shader.TileMode.CLAMP));
		canvas.drawRect(target, paint);
		paint.setShader(null);

		if ((renderedKind == SmartTopBackground.Kind.ARTWORK) && (artwork != null)) {
			drawCenterCrop(canvas, bounds, artwork);
		} else if (renderedKind == SmartTopBackground.Kind.AUDIO_SPECTRUM) {
			drawSpectrum(canvas, bounds);
		}

		int start = (renderedKind == SmartTopBackground.Kind.ARTWORK) ? 0x2E080D15 : 0x52080D15;
		int end = (renderedKind == SmartTopBackground.Kind.ARTWORK) ? 0xE0070B11 : 0xF0070B11;
		paint.setShader(new LinearGradient(bounds.left, 0F, bounds.right, 0F,
				start, end, Shader.TileMode.CLAMP));
		canvas.drawRect(target, paint);
		paint.setShader(null);

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(Math.max(1F, getDensity()));
		paint.setColor(0x857AA7FF);
		canvas.drawRoundRect(target, radiusPx, radiusPx, paint);
		paint.setStyle(Paint.Style.FILL);
		canvas.restoreToCount(save);
	}

	private void drawCenterCrop(Canvas canvas, Rect bounds, Bitmap bitmap) {
		float scale = Math.max((float) bounds.width() / bitmap.getWidth(),
				(float) bounds.height() / bitmap.getHeight());
		int srcWidth = Math.max(1, Math.round(bounds.width() / scale));
		int srcHeight = Math.max(1, Math.round(bounds.height() / scale));
		int left = Math.max(0, (bitmap.getWidth() - srcWidth) / 2);
		int top = Math.max(0, (bitmap.getHeight() - srcHeight) / 2);
		source.set(left, top, Math.min(bitmap.getWidth(), left + srcWidth),
				Math.min(bitmap.getHeight(), top + srcHeight));
		canvas.drawBitmap(bitmap, source, bounds, paint);
	}

	private void drawSpectrum(Canvas canvas, Rect bounds) {
		float areaWidth = bounds.width() * 0.52F;
		float gap = areaWidth / (SPECTRUM.length * 2F + 1F);
		float barWidth = gap;
		float bottom = bounds.bottom - bounds.height() * 0.12F;
		float maxHeight = bounds.height() * 0.62F;
		paint.setColor(0x347AA7FF);
		for (int i = 0; i < SPECTRUM.length; i++) {
			float left = bounds.left + gap + i * gap * 2F;
			float top = bottom - maxHeight * SPECTRUM[i];
			canvas.drawRoundRect(left, top, left + barWidth, bottom,
					barWidth * 0.5F, barWidth * 0.5F, paint);
		}
	}

	private float getDensity() {
		return getCallback() instanceof android.view.View view ?
				view.getResources().getDisplayMetrics().density : 1F;
	}

	@Override
	public void setAlpha(int alpha) {
		paint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
		paint.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}
}
```

Before production use, move density into the constructor instead of relying on the drawable
callback, inset the border by half its stroke width and add the Empty/source watermark only if it
matches the approved demo. Keep the bar array static.

## 6. Preserve ripple without adding a child view

Factory scaffold:

```java
static RenderedBackground createBackground(Context context, SmartTopBackground.Kind kind) {
	float density = context.getResources().getDisplayMetrics().density;
	float radius = 14F * density;
	SmartTopCardBackgroundDrawable content =
			new SmartTopCardBackgroundDrawable(radius, kind);

	GradientDrawable mask = new GradientDrawable();
	mask.setShape(GradientDrawable.RECTANGLE);
	mask.setCornerRadius(radius);
	mask.setColor(Color.WHITE);

	TypedArray attrs = context.obtainStyledAttributes(new int[]{R.attr.rippleColor});
	ColorStateList rippleColor = attrs.getColorStateList(0);
	attrs.recycle();
	if (rippleColor == null) rippleColor = ColorStateList.valueOf(0x33FFFFFF);

	return new RenderedBackground(content,
			new RippleDrawable(rippleColor, content, mask));
}

record RenderedBackground(
		SmartTopCardBackgroundDrawable content,
		RippleDrawable ripple) {
}
```

The feature-off path must continue to call:

```java
views.root().setBackgroundResource(R.drawable.dashboard_smart_top_bg);
```

## 7. Binder split and stale-result guard

Adapt `SmartTopBinder.Views.artwork` to `sourceIcon`, then split binding:

```java
private void bindSourceIcon(Views views, SmartTopViewState state) {
	ImageView icon = views.sourceIcon();
	icon.setImageTintList(context.getColorStateList(R.color.dashboard_smart_action_v2_tint));
	icon.setImageResource(state.icon());
}

private void bindBackground(Views views, SmartTopViewState state) {
	View root = views.root();
	SmartTopBackground background = state.background();
	RenderedBackground rendered = createBackground(context, background.kind());
	BackgroundBindToken token = new BackgroundBindToken(state.generation(),
			itemId(state.presentedItem()), background.identity());
	root.setTag(R.id.dashboard_smart_background_bind_token, token);
	root.setTag(R.id.dashboard_smart_background_drawable_tag, rendered.content());
	root.setBackground(rendered.ripple());

	if ((background.kind() != SmartTopBackground.Kind.ARTWORK) ||
			(background.artworkUri() == null) || (state.presentedItem() == null)) return;

	state.presentedItem().getLib().getBitmapCache()
			.getBitmapIfCached(context, background.artworkUri().toString(), false)
			.main().onSuccess(bitmap -> {
				if ((bitmap == null) || !token.equals(root.getTag(
						R.id.dashboard_smart_background_bind_token))) return;
				if (!SmartTopBackgroundPolicy.eligibleDimensions(
						bitmap.getWidth(), bitmap.getHeight(), false)) return;
				Object current = root.getTag(R.id.dashboard_smart_background_drawable_tag);
				if (current == rendered.content()) rendered.content().setArtwork(bitmap);
			});
}

record BackgroundBindToken(long generation, String itemId, String backgroundIdentity) {
}
```

Add tag IDs to `fermata/src/main/res/values/ids.xml`:

```xml
<item name="dashboard_smart_background_bind_token" type="id" />
<item name="dashboard_smart_background_drawable_tag" type="id" />
```

Call `bindSourceIcon()` and `bindBackground()` only from a full bind. Do not call either method from
`bindTimelineUpdate()`.

## 8. State-preservation pattern

Every copy method must carry `background` forward:

```java
public SmartTopViewState withTimeline(SmartTopTimeline nextTimeline) {
	return new SmartTopViewState(generation, mode, layout, presentedItem, canonicalItem,
			icon, background, eyebrow, title, subtitle, nextTimeline, capabilities, actions,
			favorite, quickRecent, providerResult);
}
```

Apply the same rule to `withLayout`, `withTitle` and `withQuickRecent`. Constructor argument order is
a migration hazard; compile after updating all call sites before making any binder change.

## 9. OSS references reviewed

| Reference | Reviewed revision | License | Decision |
| --- | --- | --- | --- |
| RetroMusicPlayer `PaletteExtensions.kt` | `3b0dcc18019ce360911cea73b3de852b353d4f4f` | GPL-3.0 | Reference only; its 2:1 threshold is too low for SmartTop text |
| `massoudss/waveformSeekBar` | `a0996b8c650708e3cc0ab3e5ad45ab4076a14f65` | README declares Apache-2.0 | Do not vendor; the static bar renderer above is purpose-written |
| `wasabeef/Blurry` | `d53d019cffb630452be88215781ac4c410c543f2` | Apache-2.0 | Rejected; runtime blur and RenderScript are unnecessary |
| Android UAMP | `9498d991ab84b708e2130b6217483d4cccd4bbfd` | Apache-2.0 | Rejected; its provider downloads artwork on a miss |
| AndroidX `ColorUtils`/Palette | current AndroidX API | Apache-2.0 | Use public API if dynamic contrast is added later |

Links:

- https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/dev/app/src/main/java/code/name/monkey/retromusic/extensions/PaletteExtensions.kt
- https://github.com/massoudss/waveformSeekBar/blob/master/lib/src/main/java/com/masoudss/lib/WaveformSeekBar.kt
- https://github.com/wasabeef/Blurry
- https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/library/AlbumArtContentProvider.kt
- https://developer.android.com/reference/androidx/core/graphics/ColorUtils
- https://developer.android.com/reference/androidx/palette/graphics/Palette
- https://www.gnu.org/licenses/license-list.en.html#apache2

If an implementation later copies third-party source rather than using these FermataX scaffolds:

1. pin the exact upstream commit;
2. retain the file-level copyright/license header;
3. mark the file as modified;
4. add the component, URL, revision and copied files to `THIRD_PARTY_NOTICES.md`;
5. include required Apache `LICENSE`/`NOTICE` material in distributions;
6. rerun a license compatibility review before merging.
