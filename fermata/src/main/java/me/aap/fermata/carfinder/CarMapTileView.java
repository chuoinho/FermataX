package me.aap.fermata.carfinder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.fermata.R;

/**
 * Self-contained Canvas-based map view.
 * Renders dark vector/raster map tiles (MapTiler Dataviz Dark / OSM fallback)
 * and pins without external heavyweight map SDKs.
 * Styled with FermataX's signature dark navy & electric blue theme.
 */
public final class CarMapTileView extends View {
	private static final int TILE_SIZE = 256;
	private static final int ACCENT_BLUE = 0xFF3B82F6;
	private static final LruCache<String, Bitmap> MEM_CACHE = new LruCache<>(64);
	private static final Set<String> PENDING_TILES = new HashSet<>();
	private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
	private static final ExecutorService TILE_EXECUTOR = Executors.newFixedThreadPool(6);
	private static final String[] GOOGLE_TILE_SERVERS = {
			"https://mt0.google.com/vt/lyrs=m&x=%d&y=%d&z=%d",
			"https://mt1.google.com/vt/lyrs=m&x=%d&y=%d&z=%d",
			"https://mt2.google.com/vt/lyrs=m&x=%d&y=%d&z=%d",
			"https://mt3.google.com/vt/lyrs=m&x=%d&y=%d&z=%d"
	};

	private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint userPinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint carPinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();
	private final Rect textBounds = new Rect();
	private final RectF badgeRect = new RectF();
	private final RectF tileDstRect = new RectF();

	private Drawable carDrawable;
	private CarParkingLocation carLocation;
	private Location userLocation;
	private boolean nightMode = false;
	private android.graphics.ColorMatrixColorFilter nightModeFilter;

	public CarMapTileView(Context context) {
		super(context);
		initMapPaints(context);
	}

	public CarMapTileView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		initMapPaints(context);
	}

	public void setNightMode(boolean night) {
		if (this.nightMode == night && (nightModeFilter != null || !night)) return;
		this.nightMode = night;
		if (night) {
			if (nightModeFilter == null) {
				android.graphics.ColorMatrix invert = new android.graphics.ColorMatrix(new float[] {
						-0.82f,  0.00f,  0.00f, 0f, 230f,
						 0.00f, -0.82f,  0.00f, 0f, 230f,
						 0.00f,  0.00f, -0.82f, 0f, 240f,
						 0.00f,  0.00f,  0.00f, 1f,   0f
				});
				android.graphics.ColorMatrix tint = new android.graphics.ColorMatrix();
				tint.setScale(0.85f, 0.90f, 1.05f, 1.0f);
				invert.preConcat(tint);
				nightModeFilter = new android.graphics.ColorMatrixColorFilter(invert);
			}
			tilePaint.setColorFilter(nightModeFilter);
		} else {
			tilePaint.setColorFilter(null);
		}
		invalidate();
	}

	public boolean isNightMode() {
		return nightMode;
	}

	private void initMapPaints(Context context) {
		float density = getResources().getDisplayMetrics().density;

		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeCap(Paint.Cap.ROUND);
		linePaint.setStrokeWidth(2.5f * density);
		linePaint.setColor(0xCC3B82F6); // Fermata electric blue dashed route line
		linePaint.setPathEffect(new DashPathEffect(new float[]{6f * density, 6f * density}, 0f));

		userPinPaint.setStyle(Paint.Style.FILL);
		carPinPaint.setStyle(Paint.Style.FILL);

		textPaint.setColor(0xFFFFFFFF);
		textPaint.setTextSize(11f * density);
		textPaint.setFakeBoldText(true);

		textBgPaint.setStyle(Paint.Style.FILL);

		try {
			carDrawable = ContextCompat.getDrawable(context, R.drawable.ic_car_find);
		} catch (Throwable ignored) {
		}
	}

	/**
	 * Updates vehicle parking and current user locations and invalidates view.
	 */
	public void updateVehicleAndUserLocations(@Nullable CarParkingLocation carLoc, @Nullable Location userLoc) {
		this.carLocation = carLoc;
		this.userLocation = userLoc;
		invalidate();
	}

	/** Backwards-compatible alias */
	public void setPositions(@Nullable CarParkingLocation carLoc, @Nullable Location userLoc) {
		updateVehicleAndUserLocations(carLoc, userLoc);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) return;

		canvas.drawColor(nightMode ? 0xFF14181B : 0xFFE5E9EC); // Dark/Light background while tiles load

		boolean hasCar = (carLocation != null && carLocation.isValid());
		boolean hasUser = (userLocation != null);

		if (!hasCar && !hasUser) {
			// Center on default fallback coordinates (Hanoi center: 21.0285, 105.8542)
			renderMapCanvas(canvas, 21.0285, 105.8542, 21.0285, 105.8542, 16, width, height);
			return;
		}

		if (hasCar && hasUser) {
			double carLat = carLocation.getLatitude();
			double carLng = carLocation.getLongitude();
			double userLat = userLocation.getLatitude();
			double userLng = userLocation.getLongitude();

			double minLat = Math.min(carLat, userLat);
			double maxLat = Math.max(carLat, userLat);
			double minLng = Math.min(carLng, userLng);
			double maxLng = Math.max(carLng, userLng);

			int zoom = computeOptimalZoomLevel(carLat, carLng, userLat, userLng, width, height);
			renderMapCanvas(canvas, minLat, minLng, maxLat, maxLng, zoom, width, height);
		} else if (hasCar) {
			double carLat = carLocation.getLatitude();
			double carLng = carLocation.getLongitude();
			renderMapCanvas(canvas, carLat, carLng, carLat, carLng, 18, width, height);
		} else {
			double userLat = userLocation.getLatitude();
			double userLng = userLocation.getLongitude();
			renderMapCanvas(canvas, userLat, userLng, userLat, userLng, 18, width, height);
		}
	}

	private void renderMapCanvas(Canvas canvas, double minLat, double minLng, double maxLat, double maxLng,
			int zoom, int width, int height) {
		double midLat = (minLat + maxLat) / 2.0;
		double midLng = (minLng + maxLng) / 2.0;

		double centerTileX = convertLongitudeToTileX(midLng, zoom);
		double centerTileY = convertLatitudeToTileY(midLat, zoom);

		double originPxX = (width / 2.0) - (centerTileX * TILE_SIZE);
		double originPxY = (height / 2.0) - (centerTileY * TILE_SIZE);

		int minTileX = (int) Math.floor(-originPxX / TILE_SIZE);
		int maxTileX = (int) Math.floor((width - originPxX) / TILE_SIZE);
		int minTileY = (int) Math.floor(-originPxY / TILE_SIZE);
		int maxTileY = (int) Math.floor((height - originPxY) / TILE_SIZE);

		int maxTileIndex = 1 << zoom;

		// Draw map tiles
		for (int ty = minTileY; ty <= maxTileY; ty++) {
			if (ty < 0 || ty >= maxTileIndex) continue;
			for (int tx = minTileX; tx <= maxTileX; tx++) {
				int normalizedTx = ((tx % maxTileIndex) + maxTileIndex) % maxTileIndex;
				Bitmap tileBmp = loadOrFetchMapTileBitmap(zoom, normalizedTx, ty);
				float left = (float) (originPxX + (tx * TILE_SIZE));
				float top = (float) (originPxY + (ty * TILE_SIZE));
				tileDstRect.set(left, top, left + TILE_SIZE, top + TILE_SIZE);
				if (tileBmp != null) {
					canvas.drawBitmap(tileBmp, null, tileDstRect, tilePaint);
				}
			}
		}

		float density = getResources().getDisplayMetrics().density;
		boolean hasCar = (carLocation != null && carLocation.isValid());
		boolean hasUser = (userLocation != null);

		float userPxX = 0f;
		float userPxY = 0f;
		float carPxX = 0f;
		float carPxY = 0f;

		if (hasUser) {
			userPxX = (float) (originPxX + (convertLongitudeToTileX(userLocation.getLongitude(), zoom) * TILE_SIZE));
			userPxY = (float) (originPxY + (convertLatitudeToTileY(userLocation.getLatitude(), zoom) * TILE_SIZE));
		}

		if (hasCar) {
			carPxX = (float) (originPxX + (convertLongitudeToTileX(carLocation.getLongitude(), zoom) * TILE_SIZE));
			carPxY = (float) (originPxY + (convertLatitudeToTileY(carLocation.getLatitude(), zoom) * TILE_SIZE));
		}

		// Draw connecting dashed line if both pins are present
		if (hasCar && hasUser && (Math.abs(carPxX - userPxX) > 4 || Math.abs(carPxY - userPxY) > 4)) {
			path.reset();
			path.moveTo(userPxX, userPxY);
			path.lineTo(carPxX, carPxY);
			canvas.drawPath(path, linePaint);
		}

		// Draw User pin (Fermata sky cyan style)
		if (hasUser) {
			userPinPaint.setColor(0x3338BDF8);
			canvas.drawCircle(userPxX, userPxY, 20f * density, userPinPaint);
			userPinPaint.setColor(0xFF38BDF8);
			canvas.drawCircle(userPxX, userPxY, 9f * density, userPinPaint);
			userPinPaint.setColor(0xFFFFFFFF);
			canvas.drawCircle(userPxX, userPxY, 3.5f * density, userPinPaint);
			drawLocationPillLabel(canvas, userPxX, userPxY + (14f * density), getContext().getString(R.string.car_finder_you), 0xEE131926);
		}

		// Draw Car Pin (Fermata electric blue style) & Uncertainty Circle
		if (hasCar) {
			float uncM = carLocation.getUncertainty();
			if (uncM <= 0f) uncM = carLocation.getAccuracy();
			if (uncM > 0f) {
				double latRad = Math.toRadians(carLocation.getLatitude());
				double metersPerPx = (40075016.686 * Math.cos(latRad)) / (TILE_SIZE * (1 << zoom));
				float uncPx = (float) Math.max(26f * density, (metersPerPx > 0.0) ? (uncM / metersPerPx) : (26f * density));

				int strokeColor = 0x883B82F6;
				int fillColor = 0x183B82F6;
				if (carLocation.isBasement()) {
					strokeColor = 0x998B5CF6;
					fillColor = 0x228B5CF6;
				} else if (uncM > 35.0f) {
					strokeColor = 0x8860A5FA;
					fillColor = 0x1860A5FA;
				}

				carPinPaint.setStyle(Paint.Style.FILL);
				carPinPaint.setColor(fillColor);
				canvas.drawCircle(carPxX, carPxY, uncPx, carPinPaint);

				carPinPaint.setStyle(Paint.Style.STROKE);
				carPinPaint.setStrokeWidth(1.5f * density);
				carPinPaint.setColor(strokeColor);
				canvas.drawCircle(carPxX, carPxY, uncPx, carPinPaint);
				carPinPaint.setStyle(Paint.Style.FILL);
			}

			// Soft outer halo + electric blue pin
			carPinPaint.setColor(0x333B82F6);
			canvas.drawCircle(carPxX, carPxY, 24f * density, carPinPaint);
			carPinPaint.setColor(ACCENT_BLUE);
			canvas.drawCircle(carPxX, carPxY, 15f * density, carPinPaint);

			if (carDrawable != null) {
				int sz = Math.round(18f * density);
				int cx = Math.round(carPxX);
				int cy = Math.round(carPxY);
				carDrawable.setBounds(cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2);
				carDrawable.setTint(0xFFFFFFFF); // Crisp white car silhouette on blue pin
				carDrawable.draw(canvas);
			}

			String carLabel = getContext().getString(R.string.car_finder_my_car);
			if (carLocation.isBasement()) {
				carLabel += " (P)";
			}
			drawLocationPillLabel(canvas, carPxX, carPxY + (20f * density), carLabel, 0xEE131926);
		}

		// Floating Top-Left Badge (Đỗ ... trước)
		if (hasCar) {
			long elapsedMinutes = Math.max(0, (System.currentTimeMillis() - carLocation.getTimestamp()) / 60000L);
			String timeStr;
			if (elapsedMinutes < 60) {
				timeStr = getContext().getString(R.string.car_finder_parked_ago_minutes, elapsedMinutes);
			} else {
				timeStr = getContext().getString(R.string.car_finder_parked_ago_hours, elapsedMinutes / 60);
			}
			drawFloatingTimeBadge(canvas, 16f * density, 16f * density, timeStr);
		}
	}

	private void drawFloatingTimeBadge(Canvas canvas, float x, float y, String text) {
		float density = getResources().getDisplayMetrics().density;
		textPaint.getTextBounds(text, 0, text.length(), textBounds);
		float textWidth = textPaint.measureText(text);
		float padX = 12f * density;
		float padY = 6f * density;
		float height = textBounds.height();

		badgeRect.set(x, y, x + textWidth + padX * 2f, y + height + padY * 2f);
		textBgPaint.setColor(0xEE131926); // Fermata dark navy surface
		canvas.drawRoundRect(badgeRect, 14f * density, 14f * density, textBgPaint);

		textBgPaint.setStyle(Paint.Style.STROKE);
		textBgPaint.setStrokeWidth(1f * density);
		textBgPaint.setColor(0x443B82F6); // Subtle blue border
		canvas.drawRoundRect(badgeRect, 14f * density, 14f * density, textBgPaint);
		textBgPaint.setStyle(Paint.Style.FILL);

		canvas.drawText(text, x + padX, y + height + padY - 2f * density, textPaint);
	}

	private void drawLocationPillLabel(Canvas canvas, float x, float y, String text, int bgColor) {
		float density = getResources().getDisplayMetrics().density;
		textPaint.getTextBounds(text, 0, text.length(), textBounds);
		float textWidth = textPaint.measureText(text);
		float padX = 8f * density;
		float padY = 4f * density;
		float height = textBounds.height();

		badgeRect.set(x - textWidth / 2f - padX, y, x + textWidth / 2f + padX, y + height + padY * 2f);
		textBgPaint.setColor(bgColor);
		canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, textBgPaint);

		textBgPaint.setStyle(Paint.Style.STROKE);
		textBgPaint.setStrokeWidth(1f * density);
		textBgPaint.setColor(0x33263552);
		canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, textBgPaint);
		textBgPaint.setStyle(Paint.Style.FILL);

		canvas.drawText(text, x - textWidth / 2f, y + height + padY - 2f * density, textPaint);
	}

	private int computeOptimalZoomLevel(double carLat, double carLng, double userLat, double userLng, int w, int h) {
		float dist = AngleUtils.distanceBetween(userLat, userLng, carLat, carLng);
		if (dist <= 40f) {
			return 19; // Close range street & building level
		} else if (dist <= 100f) {
			return 18;
		} else if (dist <= 260f) {
			return 17;
		} else if (dist <= 600f) {
			return 16;
		}

		double minLat = Math.min(carLat, userLat);
		double maxLat = Math.max(carLat, userLat);
		double minLng = Math.min(carLng, userLng);
		double maxLng = Math.max(carLng, userLng);

		float pad = 40f * getResources().getDisplayMetrics().density;
		int availableW = Math.max(10, Math.round(w - pad));
		int availableH = Math.max(10, Math.round(h - pad));

		for (int z = 15; z >= 3; z--) {
			double dx = Math.abs(convertLongitudeToTileX(maxLng, z) - convertLongitudeToTileX(minLng, z)) * TILE_SIZE;
			double dy = Math.abs(convertLatitudeToTileY(maxLat, z) - convertLatitudeToTileY(minLat, z)) * TILE_SIZE;
			if (dx <= availableW && dy <= availableH) {
				return z;
			}
		}
		return 12;
	}

	private double convertLongitudeToTileX(double lon, int zoom) {
		return ((lon + 180.0) / 360.0) * (1 << zoom);
	}

	private double convertLatitudeToTileY(double lat, int zoom) {
		double rad = Math.toRadians(Math.max(-85.0511, Math.min(85.0511, lat)));
		return ((1.0 - (Math.log(Math.tan(rad) + (1.0 / Math.cos(rad))) / Math.PI)) / 2.0) * (1 << zoom);
	}

	@Nullable
	private Bitmap loadOrFetchMapTileBitmap(int z, int x, int y) {
		String key = z + "_" + x + "_" + y;
		Bitmap cached = MEM_CACHE.get(key);
		if (cached != null) return cached;

		File cacheDir = new File(getContext().getCacheDir(), "tiles_map_cache");
		if (!cacheDir.exists()) cacheDir.mkdirs();
		File tileFile = new File(cacheDir, key + ".png");

		if (tileFile.exists() && tileFile.length() > 0) {
			Bitmap diskBmp = BitmapFactory.decodeFile(tileFile.getAbsolutePath());
			if (diskBmp != null) {
				MEM_CACHE.put(key, diskBmp);
				return diskBmp;
			}
		}

		synchronized (PENDING_TILES) {
			if (PENDING_TILES.contains(key)) return null;
			PENDING_TILES.add(key);
		}

		TILE_EXECUTOR.execute(() -> {
			byte[] bytes = downloadMapTileData(z, x, y);
			if (bytes != null && bytes.length > 0) {
				Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				if (bmp != null) {
					MEM_CACHE.put(key, bmp);
					try (FileOutputStream fos = new FileOutputStream(tileFile)) {
						fos.write(bytes);
					} catch (Throwable ignored) {
					}
					pruneDiskCacheIfNeeded(cacheDir);
					UI_HANDLER.post(this::invalidate);
				}
			}
			synchronized (PENDING_TILES) {
				PENDING_TILES.remove(key);
			}
		});

		return null;
	}

	private static final int MAX_DISK_TILES = 500;
	private static final int PRUNE_TARGET_TILES = 400;

	private static void pruneDiskCacheIfNeeded(File cacheDir) {
		File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".png"));
		if (files == null || files.length <= MAX_DISK_TILES) return;
		java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
		int toDelete = files.length - PRUNE_TARGET_TILES;
		for (int i = 0; i < toDelete; i++) {
			files[i].delete();
		}
	}

	@Nullable
	private byte[] downloadMapTileData(int z, int x, int y) {
		// 1. Primary: Direct Google Maps tiles (zero API key, zero watermark, ultra-fast Google Global Cache)
		int serverIdx = Math.abs(x + y) % GOOGLE_TILE_SERVERS.length;
		String urlGoogle = String.format(Locale.US, GOOGLE_TILE_SERVERS[serverIdx], x, y, z);
		byte[] data = fetchHttpResourceBytes(urlGoogle);
		if (data != null && data.length > 200) {
			return data;
		}

		// 2. Secondary fallback: OpenStreetMap Germany mirror (zero API key, public, no watermarks)
		String urlOsm = String.format(Locale.US, "https://tile.openstreetmap.de/%d/%d/%d.png", z, x, y);
		return fetchHttpResourceBytes(urlOsm);
	}

	@Nullable
	private byte[] fetchHttpResourceBytes(String urlStr) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setConnectTimeout(3500);
			conn.setReadTimeout(4000);
			conn.setRequestProperty("User-Agent", "FermataX/2.0.1 (Android)");
			conn.setRequestProperty("Connection", "Keep-Alive");
			if (conn.getResponseCode() == 200) {
				try (InputStream in = conn.getInputStream();
					 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					byte[] buf = new byte[8192];
					int read;
					while ((read = in.read(buf)) > 0) {
						baos.write(buf, 0, read);
					}
					return baos.toByteArray();
				}
			}
		} catch (Throwable ignored) {
		}
		// Do not call conn.disconnect() to preserve TCP/SSL keep-alive socket reuse in Android's HTTP pool
		return null;
	}
}
