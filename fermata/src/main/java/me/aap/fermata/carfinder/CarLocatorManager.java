package me.aap.fermata.carfinder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.location.LocationHub;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.log.Log;

/**
 * Singleton orchestrator for vehicular location tracking, zero-delay T0 disconnect anchoring,
 * and high-accuracy parking state estimation.
 * Supports both wired (USB power disconnect) and wireless (Bluetooth ACL / Wi-Fi disconnect) triggers.
 */
public final class CarLocatorManager {
	private static final String CHANNEL_ID = "fermata_car_finder_channel";
	private static final int NOTIF_ID = 4040;
	private static final long DEBOUNCE_DISCONNECT_MS = 10_000L; // 10s debounce to prevent duplicate anchors
	private static CarLocatorManager INSTANCE;

	public interface UserLocationListener {
		void onUserLocationChanged(Location location);
	}

	private final LocationKalmanFilter kalmanFilter = new LocationKalmanFilter();
	private final ParkingLocationSmoother smoother = new ParkingLocationSmoother();
	private final Set<UserLocationListener> userLocationListeners = new CopyOnWriteArraySet<>();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final LocationHub.Listener hubListener = location -> mainHandler.post(() -> handleTrackedLocation(location));

	private Context appContext;
	private Location currentUserLocation;
	private boolean isTrackingLocation;
	private boolean isTrackingVehicularLocation;
	private boolean isDisconnectReceiverRegistered;
	private long lastDisconnectAnchorTime = 0L;

	private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
		@Override
		@SuppressLint("MissingPermission")
		public void onReceive(Context context, Intent intent) {
			if (intent == null || intent.getAction() == null) return;
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device != null) {
					try {
						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
								|| ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
							BluetoothClass btClass = device.getBluetoothClass();
							if (btClass != null) {
								int major = btClass.getMajorDeviceClass();
								int deviceClass = btClass.getDeviceClass();
								boolean isVehicular = (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO)
										|| (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE);
								if (!isVehicular) {
									Log.i("CarFinder: Ignoring BT disconnect from non-vehicular device (major=", major, ", class=", deviceClass, ")");
									return;
								}
							}
						}
					} catch (SecurityException e) {
						Log.w("CarFinder: SecurityException reading BluetoothClass", e);
					}
				}
				if (me.aap.fermata.auto.AutomotiveConnectionState.get().isProjectionConnected()
						|| me.aap.fermata.media.service.AutomotiveRuntimeGate.isProjectionActive()) {
					Log.i("CarFinder: Automotive projection is still active; ignoring peripheral BT disconnect");
					return;
				}
				Log.i("CarFinder: Wireless Bluetooth ACL disconnected trigger");
				handleAutomotiveDisconnection(context, "bluetooth_acl");
			} else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
				if (!isTrackingVehicularLocation) {
					Log.i("CarFinder: Ignoring power disconnect outside active vehicular drive");
					return;
				}
				if (me.aap.fermata.auto.AutomotiveConnectionState.get().isProjectionConnected()
						|| me.aap.fermata.media.service.AutomotiveRuntimeGate.isProjectionActive()) {
					Log.i("CarFinder: Automotive projection is still active; ignoring power disconnect");
					return;
				}
				Log.i("CarFinder: Wired USB power disconnected trigger");
				handleAutomotiveDisconnection(context, "power_usb");
			}
		}
	};

	public static synchronized CarLocatorManager get() {
		if (INSTANCE == null) {
			INSTANCE = new CarLocatorManager();
		}
		return INSTANCE;
	}

	private CarLocatorManager() {
	}

	public LocationKalmanFilter getKalmanFilter() {
		return kalmanFilter;
	}

	public ParkingLocationSmoother getSmoother() {
		return smoother;
	}

	@Nullable
	public Location getCurrentUserLocation() {
		return currentUserLocation;
	}

	/** Input for the parking fix at disconnect. */
	@Nullable
	static Location chooseParkingInput(@Nullable Location bufferNewest, @Nullable Location system, long nowElapsedMs) {
		long bufferAge = (bufferNewest != null && bufferNewest.getElapsedRealtimeNanos() > 0)
				? (long) (nowElapsedMs - bufferNewest.getElapsedRealtimeNanos() / 1e6)
				: (bufferNewest != null ? (System.currentTimeMillis() - bufferNewest.getTime()) : Long.MAX_VALUE);
		long systemAge = (system != null && system.getElapsedRealtimeNanos() > 0)
				? (long) (nowElapsedMs - system.getElapsedRealtimeNanos() / 1e6)
				: (system != null ? (System.currentTimeMillis() - system.getTime()) : Long.MAX_VALUE);

		boolean bufferStale = (bufferNewest == null || bufferAge > 30_000L);
		boolean systemFresh = (system != null && systemAge <= 30_000L);

		if (bufferStale && systemFresh) {
			return system;
		}
		return bufferNewest;
	}

	/**
	 * Queries the best known current or last-known location according to LocationHub precedence:
	 * 1. hub.latest() if age <= 2 minutes
	 * 2. hub.freshSystemLocation(10 minutes)
	 * 3. currentUserLocation if age <= 2 minutes
	 * 4. null
	 */
	@Nullable
	@SuppressLint("MissingPermission")
	public Location queryBestKnownUserLocation(Context context) {
		if (context != null) appContext = context.getApplicationContext();
		Context app = appContext != null ? appContext : context;
		if (app == null) return null;

		LocationHub hub = LocationHub.get(app);
		long twoMinutesMs = 2 * 60_000L;
		long tenMinutesMs = 10 * 60_000L;

		Location latest = hub.latest();
		if (latest != null && getAgeMs(latest) <= twoMinutesMs) {
			return latest;
		}

		Location freshSystem = hub.freshSystemLocation(tenMinutesMs);
		if (freshSystem != null) {
			return freshSystem;
		}

		if (currentUserLocation != null && getAgeMs(currentUserLocation) <= twoMinutesMs) {
			return currentUserLocation;
		}

		return null;
	}

	private static long getAgeMs(Location loc) {
		long elapsed = loc.getElapsedRealtimeNanos();
		if (elapsed > 0) {
			return (SystemClock.elapsedRealtimeNanos() - elapsed) / 1_000_000L;
		}
		return Math.max(0, System.currentTimeMillis() - loc.getTime());
	}

	/** Backwards-compatible alias */
	@Nullable
	public Location getBestOrLastKnownLocation(Context context) {
		return queryBestKnownUserLocation(context);
	}

	/**
	 * Called when projection connects to Android Auto (wired or wireless).
	 * Starts continuous vehicular GNSS buffering and registers disconnect listeners if auto-save is enabled.
	 */
	public synchronized void notifyAutomotiveSessionConnected() {
		Context app = FermataApplication.get();
		if (!CarFinderPrefs.isAutoSaveEnabled(app)) {
			Log.i("CarFinder: Auto-save disabled; skipping vehicular tracking and disconnect listeners on connect");
			return;
		}
		Log.i("CarFinder: Automotive connected; initializing vehicular location tracking");
		lastDisconnectAnchorTime = 0L; // Reset debounce for new drive session
		isTrackingVehicularLocation = true;
		startLocationTrackingInternal(app);
		registerDisconnectReceiver(app);
	}

	/**
	 * Completely activates or tears down background listeners and GPS tracking when the user
	 * toggles the Auto-Save feature on/off in settings.
	 */
	public synchronized void onAutoSaveSettingChanged(Context context, boolean enabled) {
		Context app = context.getApplicationContext();
		if (!enabled) {
			Log.i("CarFinder: Auto-save disabled by user; tearing down all background sensors and listeners");
			unregisterDisconnectReceiver(app);
			isTrackingVehicularLocation = false;
			smoother.clear();
			kalmanFilter.reset();
			if (userLocationListeners.isEmpty()) {
				stopLocationTrackingInternal(app);
			}
		} else {
			Log.i("CarFinder: Auto-save enabled by user");
			if (me.aap.fermata.auto.AutomotiveConnectionState.get().isProjectionConnected()) {
				notifyAutomotiveSessionConnected();
			}
		}
	}

	/** Backwards-compatible alias */
	public void onAutomotiveConnected() {
		notifyAutomotiveSessionConnected();
	}

	/**
	 * Centralized immediate T0 parking anchor handler.
	 * Detects disconnects across Wired USB, Wireless Bluetooth ACL, and CarConnection boundaries.
	 */
	public synchronized void handleAutomotiveDisconnection(Context context, String triggerSource) {
		Context app = context.getApplicationContext();
		if (!CarFinderPrefs.isAutoSaveEnabled(app)) {
			Log.i("CarFinder: Auto-save disabled; skipping capture (source=", triggerSource, ")");
			return;
		}

		if (!isTrackingVehicularLocation && !"host_unbind".equals(triggerSource)
				&& !"car_connection".equals(triggerSource) && !"legacy_auto_disconnect".equals(triggerSource)) {
			Log.i("CarFinder: Disconnect trigger (", triggerSource, ") received but not tracking vehicular session; ignoring");
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastDisconnectAnchorTime < DEBOUNCE_DISCONNECT_MS) {
			Log.d("CarFinder: Disconnect anchor debounced (source=", triggerSource, ", dt=", (now - lastDisconnectAnchorTime), "ms)");
			return;
		}
		lastDisconnectAnchorTime = now;

		// Clean up vehicular listeners
		unregisterDisconnectReceiver(app);
		isTrackingVehicularLocation = false;
		if (userLocationListeners.isEmpty()) {
			stopLocationTrackingInternal(app);
		}

		if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.w("CarFinder: Location permissions not granted; cannot anchor parking location");
			return;
		}

		Location system = LocationHub.get(app).freshSystemLocation(30_000L);
		long nowElapsedMs = SystemClock.elapsedRealtime();
		Location bestLocation = chooseParkingInput(currentUserLocation, system, nowElapsedMs);
		if (bestLocation == system) {
			Log.i("CarFinder: Chosen parking input source: system");
		} else if (bestLocation != null) {
			long age = (bestLocation.getElapsedRealtimeNanos() > 0)
					? (long) (nowElapsedMs - bestLocation.getElapsedRealtimeNanos() / 1e6)
					: (System.currentTimeMillis() - bestLocation.getTime());
			if (age > 30_000L) {
				Log.i("CarFinder: Chosen parking input source: stale");
			} else {
				Log.i("CarFinder: Chosen parking input source: buffer");
			}
		} else {
			Log.i("CarFinder: Chosen parking input source: stale");
		}

		if (bestLocation != null) {
			smoother.recordLocationSample(bestLocation);
		}

		ParkingLocationSmoother.SmoothedResult result = smoother.estimateParkedLocation(now, bestLocation);
		if (result == null || (result.latitude == 0.0 && result.longitude == 0.0)) {
			Log.w("CarFinder: Failed to obtain valid parking location at disconnect");
			return;
		}

		CarParkingLocation parkedLoc = new CarParkingLocation(
				result.latitude, result.longitude,
				result.accuracy, result.uncertainty, result.confidenceLevel,
				now, "", "", "");

		CarFinderPrefs.saveParkingLocation(app, parkedLoc);
		Log.i("CarFinder: Successfully committed immediate T0 parking anchor (source=", triggerSource,
				", conf=", result.confidenceLevel, "): lat=", result.latitude, ", lng=", result.longitude,
				" unc=", result.uncertainty);

		if (CarFinderPrefs.isNotifOnDisconnectEnabled(app)) {
			showParkingReminderNotification(app, parkedLoc);
		}

		reverseGeocodeAddressInBackground(app, parkedLoc);
	}

	/** Backwards-compatible alias */
	public void handleAutomotiveDisconnection(Context context) {
		handleAutomotiveDisconnection(context, "auto_disconnect");
	}

	/** Backwards-compatible alias */
	public void onAutomotiveDisconnect(Context context) {
		handleAutomotiveDisconnection(context, "legacy_auto_disconnect");
	}

	private synchronized void registerDisconnectReceiver(Context app) {
		if (isDisconnectReceiverRegistered) return;
		try {
			IntentFilter filter = new IntentFilter();
			filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
			filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
			app.registerReceiver(disconnectReceiver, filter);
			isDisconnectReceiverRegistered = true;
			Log.i("CarFinder: Registered wired & wireless disconnect receivers");
		} catch (Throwable t) {
			Log.w(t, "CarFinder: Failed to register disconnect receiver");
		}
	}

	private synchronized void unregisterDisconnectReceiver(Context app) {
		if (!isDisconnectReceiverRegistered) return;
		try {
			app.unregisterReceiver(disconnectReceiver);
		} catch (Throwable ignored) {
		}
		isDisconnectReceiverRegistered = false;
	}

	/**
	 * Explicitly saves a manually designated location (e.g., user presses 'Pin here').
	 */
	public void saveManualParkingLocation(Context context, Location location, @Nullable String note) {
		Context app = context.getApplicationContext();
		float acc = location.hasAccuracy() ? location.getAccuracy() : 10.0f;
		CarParkingLocation loc = new CarParkingLocation(
				location.getLatitude(), location.getLongitude(),
				acc, acc, "manual",
				System.currentTimeMillis(), "", note, "");
		CarFinderPrefs.saveParkingLocation(app, loc);
		reverseGeocodeAddressInBackground(app, loc);
	}

	/** Backwards-compatible alias */
	public void saveManualLocation(Context context, Location location, @Nullable String note) {
		saveManualParkingLocation(context, location, note);
	}

	/**
	 * Explicitly marks vehicle found, clearing location and returning to idle state.
	 */
	public void clearParkingLocationOnCarFound(Context context) {
		Context app = context.getApplicationContext();
		CarFinderPrefs.clearParkingLocation(app);
		dismissParkingNotification(app);
		Log.i("CarFinder: Vehicle marked found; cleared parking location");
	}

	/** Backwards-compatible alias */
	public void markCarFound(Context context) {
		clearParkingLocationOnCarFound(context);
	}

	private void reverseGeocodeAddressInBackground(Context app, CarParkingLocation loc) {
		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				String resolvedAddress = "";
				if (Geocoder.isPresent()) {
					Geocoder geocoder = new Geocoder(app, Locale.getDefault());
					List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
					if (addresses != null && !addresses.isEmpty()) {
						Address addr = addresses.get(0);
						StringBuilder sb = new StringBuilder();
						if (addr.getMaxAddressLineIndex() >= 0) {
							sb.append(addr.getAddressLine(0));
						} else {
							if (addr.getSubThoroughfare() != null) sb.append(addr.getSubThoroughfare()).append(" ");
							if (addr.getThoroughfare() != null) sb.append(addr.getThoroughfare()).append(", ");
							if (addr.getSubLocality() != null) sb.append(addr.getSubLocality()).append(", ");
							if (addr.getLocality() != null) sb.append(addr.getLocality());
						}
						resolvedAddress = sb.toString().trim();
					}
				}

				if (!resolvedAddress.isEmpty()) {
					CarParkingLocation updated = loc.withAddress(resolvedAddress);
					CarFinderPrefs.saveParkingLocation(app, updated);
					// Only update notification for automatic disconnects, not manual pin
					if (!"manual".equalsIgnoreCase(loc.getConfidenceLevel()) && CarFinderPrefs.isNotifOnDisconnectEnabled(app)) {
						showParkingReminderNotification(app, updated);
					}
				}
			} catch (Throwable error) {
				Log.d("CarFinder geocoding failed: ", error.getMessage());
			}
		});
	}

	private void showParkingReminderNotification(Context context, CarParkingLocation loc) {
		try {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm == null) return;

			NotificationChannel channel = new NotificationChannel(
					CHANNEL_ID,
					context.getString(R.string.car_finder_title),
					NotificationManager.IMPORTANCE_HIGH);
			channel.setDescription(context.getString(R.string.car_finder_notif_desc));
			channel.enableLights(true);
			nm.createNotificationChannel(channel);

			Intent intent = new Intent(context, MainActivity.class);
			intent.putExtra("NAV_TO_CAR_FINDER", true);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

			String title;
			String content;
			if (loc.isBasement()) {
				title = context.getString(R.string.car_finder_conf_basement);
				content = context.getString(R.string.car_finder_basement_hint);
			} else {
				int uncM = Math.round(loc.getUncertainty());
				title = context.getString(R.string.car_finder_saved_title) + " (±" + uncM + "m)";
				content = loc.getAddress().isEmpty()
						? context.getString(R.string.car_finder_parked_auto)
						: loc.getAddress();
			}

			NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
					.setSmallIcon(R.drawable.ic_car_find)
					.setContentTitle(title)
					.setContentText(content)
					.setStyle(new NotificationCompat.BigTextStyle().bigText(content))
					.setPriority(NotificationCompat.PRIORITY_HIGH)
					.setAutoCancel(true)
					.setContentIntent(pi);

			nm.notify(NOTIF_ID, builder.build());
		} catch (Throwable error) {
			Log.e(error, "CarFinder: Failed to post disconnect notification");
		}
	}

	private void dismissParkingNotification(Context context) {
		try {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm != null) nm.cancel(NOTIF_ID);
		} catch (Throwable ignored) {
		}
	}

	public synchronized void startTrackingUserLocation(Context context, UserLocationListener listener) {
		if (context != null) appContext = context.getApplicationContext();
		userLocationListeners.add(listener);
		if (isTrackingLocation && currentUserLocation != null) listener.onUserLocationChanged(currentUserLocation);
		startLocationTrackingInternal(appContext != null ? appContext : context);
	}

	private synchronized void startLocationTrackingInternal(Context app) {
		if (app != null) appContext = app.getApplicationContext();
		Context ctx = appContext != null ? appContext : app;
		if (ctx == null) return;
		if (isTrackingLocation) {
			// Retry: the hub only registers once permission is granted and location is on.
			LocationHub.get(ctx).acquire("carfinder", hubListener);
			return;
		}

		if (currentUserLocation == null) {
			Location seed = queryBestKnownUserLocation(ctx);
			if (seed != null) {
				kalmanFilter.update(seed.getLatitude(), seed.getLongitude(),
						seed.hasAccuracy() ? seed.getAccuracy() : 20.0f,
						seed.hasSpeed() ? seed.getSpeed() : -1.0f,
						seed.getTime());
				smoother.recordLocationSample(seed);
			}
		}

		LocationHub.get(ctx).acquire("carfinder", hubListener);
		isTrackingLocation = true;
		Log.i("CarFinder: Started location tracking with LocationHub (vehicular=", isTrackingVehicularLocation, ")");
	}

	private synchronized void handleTrackedLocation(@NonNull Location location) {
		currentUserLocation = location;
		kalmanFilter.update(location.getLatitude(), location.getLongitude(),
				location.hasAccuracy() ? location.getAccuracy() : 25.0f,
				location.hasSpeed() ? location.getSpeed() : -1.0f,
				location.getTime());
		smoother.recordLocationSample(location);
		for (UserLocationListener l : userLocationListeners) {
			try {
				l.onUserLocationChanged(location);
			} catch (Throwable t) {
				Log.w("CarFinder: listener error", t);
			}
		}
	}

	public synchronized void stopTrackingUserLocation(UserLocationListener listener) {
		userLocationListeners.remove(listener);
		if (userLocationListeners.isEmpty() && !isTrackingVehicularLocation) {
			Context app = appContext != null ? appContext : FermataApplication.get();
			stopLocationTrackingInternal(app);
		}
	}

	private synchronized void stopLocationTrackingInternal(@Nullable Context app) {
		if (!isTrackingLocation) return;
		Context ctx = app != null ? app : (appContext != null ? appContext : FermataApplication.get());
		if (ctx != null) {
			LocationHub.get(ctx).release("carfinder");
		}
		isTrackingLocation = false;
		Log.i("CarFinder: Stopped location tracking with LocationHub");
	}

	@VisibleForTesting
	public static synchronized void resetForTesting() {
		if (INSTANCE != null) {
			Context app = INSTANCE.appContext != null ? INSTANCE.appContext : FermataApplication.get();
			if (app != null) {
				INSTANCE.unregisterDisconnectReceiver(app);
				INSTANCE.stopLocationTrackingInternal(app);
			}
			INSTANCE.userLocationListeners.clear();
			INSTANCE.smoother.clear();
			INSTANCE.kalmanFilter.reset();
			INSTANCE.currentUserLocation = null;
			INSTANCE.isTrackingLocation = false;
			INSTANCE.isTrackingVehicularLocation = false;
		}
		INSTANCE = new CarLocatorManager();
	}
}
