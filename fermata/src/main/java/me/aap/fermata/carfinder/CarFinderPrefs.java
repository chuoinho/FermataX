package me.aap.fermata.carfinder;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;

/**
 * Manages preferences and persistence for the Car Finder feature.
 * Isolated from global FermataX settings to keep the main settings clean.
 */
public final class CarFinderPrefs {
	private static final String PREF_NAME = "fermata_car_finder";
	private static final String KEY_PARKING_LOCATION = "parking_location";
	private static final String KEY_AUTO_SAVE = "auto_save_enabled";
	private static final String KEY_NOTIF_ON_DISCONNECT = "notif_on_disconnect";
	private static final String KEY_DISTANCE_UNIT = "distance_unit"; // "m" or "ft"
	private static final String KEY_PHOTOS_PRIVATE = "photos_private";
	private static final String PHOTO_DIR = "parking_photos";

	private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	public interface Listener {
		void onParkingLocationChanged(@Nullable CarParkingLocation location);
	}

	private CarFinderPrefs() {
	}

	private static SharedPreferences getPrefs(Context context) {
		return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
	}

	public static void registerParkingLocationListener(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void unregisterParkingLocationListener(Listener listener) {
		LISTENERS.remove(listener);
	}

	/** Backwards-compatible aliases */
	public static void addListener(Listener listener) {
		registerParkingLocationListener(listener);
	}

	public static void removeListener(Listener listener) {
		unregisterParkingLocationListener(listener);
	}

	public static void registerListener(Context context, Listener listener) {
		registerParkingLocationListener(listener);
	}

	public static void unregisterListener(Context context, Listener listener) {
		unregisterParkingLocationListener(listener);
	}

	/** Internal storage: on Android 9-10 any app with storage permission can read external app files, GPS tags included. */
	public static File getPhotoDir(Context context) {
		return new File(context.getFilesDir(), PHOTO_DIR);
	}

	@Nullable
	public static CarParkingLocation retrieveParkingLocation(Context context) {
		SharedPreferences prefs = getPrefs(context);
		CarParkingLocation loc = CarParkingLocation.fromJson(prefs.getString(KEY_PARKING_LOCATION, null));
		if (!prefs.getBoolean(KEY_PHOTOS_PRIVATE, false)) movePhotosToPrivateStorage(context, prefs, loc);
		return loc;
	}

	// Photos taken by earlier versions are in external app storage
	private static void movePhotosToPrivateStorage(Context context, SharedPreferences prefs,
																								 @Nullable CarParkingLocation loc) {
		File ext = context.getExternalFilesDir(null);
		if (ext == null) return; // not mounted: try again next time
		File oldDir = new File(ext, PHOTO_DIR);
		SharedPreferences.Editor e = prefs.edit();

		try {
			String path = (loc == null) ? null : loc.getPhotoPath();
			if ((path != null) && path.startsWith(oldDir.getPath() + File.separator)) {
				File src = new File(path);
				File dst = new File(getPhotoDir(context), src.getName());
				loc.setPhotoPath((FileUtils.move(src, dst) > 0) ? dst.getAbsolutePath() : null);
				e.putString(KEY_PARKING_LOCATION, loc.toJson());
			}
			FileUtils.delete(oldDir); // photos of earlier spots that were never removed
		} catch (Exception ex) {
			Log.e(ex, "Failed to move parking photos to private storage"); // the photo stays put: retry next time
			return;
		}

		e.putBoolean(KEY_PHOTOS_PRIVATE, true).apply();
	}

	@Nullable
	public static CarParkingLocation getParkingLocation(Context context) {
		return retrieveParkingLocation(context);
	}

	public static void persistParkingLocation(Context context, CarParkingLocation location) {
		getPrefs(context).edit().putString(KEY_PARKING_LOCATION, location.toJson()).apply();
		notifyLocationChanged(location);
	}

	public static void saveParkingLocation(Context context, CarParkingLocation location) {
		persistParkingLocation(context, location);
	}

	public static void deleteParkingLocation(Context context) {
		getPrefs(context).edit().remove(KEY_PARKING_LOCATION).apply();
		notifyLocationChanged(null);
	}

	public static void clearParkingLocation(Context context) {
		deleteParkingLocation(context);
	}

	public static boolean isAutoSaveEnabled(Context context) {
		return getPrefs(context).getBoolean(KEY_AUTO_SAVE, true);
	}

	public static void setAutoSaveEnabled(Context context, boolean enabled) {
		getPrefs(context).edit().putBoolean(KEY_AUTO_SAVE, enabled).apply();
		CarLocatorManager.get().onAutoSaveSettingChanged(context, enabled);
	}

	public static boolean isNotifOnDisconnectEnabled(Context context) {
		return getPrefs(context).getBoolean(KEY_NOTIF_ON_DISCONNECT, true);
	}

	public static void setNotifOnDisconnectEnabled(Context context, boolean enabled) {
		getPrefs(context).edit().putBoolean(KEY_NOTIF_ON_DISCONNECT, enabled).apply();
	}

	public static String getDistanceUnit(Context context) {
		return getPrefs(context).getString(KEY_DISTANCE_UNIT, "m");
	}

	private static void notifyLocationChanged(@Nullable CarParkingLocation location) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			for (Listener l : LISTENERS) {
				l.onParkingLocationChanged(location);
			}
		} else {
			MAIN_HANDLER.post(() -> {
				for (Listener l : LISTENERS) {
					l.onParkingLocationChanged(location);
				}
			});
		}
	}
}
