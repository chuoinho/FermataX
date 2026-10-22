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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
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
	private static final CarLocatorManager INSTANCE = new CarLocatorManager();

	public interface UserLocationListener {
		void onUserLocationChanged(Location location);
	}

	private final LocationKalmanFilter kalmanFilter = new LocationKalmanFilter();
	private final ParkingLocationSmoother smoother = new ParkingLocationSmoother();
	private final Set<UserLocationListener> userLocationListeners = new CopyOnWriteArraySet<>();

	private Location currentUserLocation;
	private LocationListener activeLocationListener;
	private LocationListener networkLocationListener;
	private LocationListener fusedLocationListener;
	private LocationListener passiveLocationListener;
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

	public static CarLocatorManager get() {
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

	/**
	 * Queries the best known current or last-known location across GPS, Network, and Fused providers.
	 */
	@Nullable
	@SuppressLint("MissingPermission")
	public Location queryBestKnownUserLocation(Context context) {
		if (currentUserLocation != null) return currentUserLocation;
		Context app = context.getApplicationContext();
		if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return null;
		}
		try {
			LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
			if (lm != null) {
				Location best = null;
				String[] providers = {
						LocationManager.GPS_PROVIDER,
						LocationManager.NETWORK_PROVIDER,
						LocationManager.PASSIVE_PROVIDER
				};
				for (String p : providers) {
					try {
						Location cand = lm.getLastKnownLocation(p);
						if (cand != null) {
							if (best == null || cand.getTime() > best.getTime()
									|| (cand.getTime() >= best.getTime() - 10_000L && cand.getAccuracy() < best.getAccuracy())) {
								best = cand;
							}
						}
					} catch (Throwable ignored) {}
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					try {
						Location cand = lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
						if (cand != null) {
							if (best == null || cand.getTime() > best.getTime()
									|| (cand.getTime() >= best.getTime() - 10_000L && cand.getAccuracy() < best.getAccuracy())) {
								best = cand;
							}
						}
					} catch (Throwable ignored) {}
				}
				if (best != null) {
					currentUserLocation = best;
					return best;
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
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

		Location bestLocation = sampleLatestAccurateLocation(app);
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

	@Nullable
	private Location sampleLatestAccurateLocation(Context app) {
		if (kalmanFilter.isInitialized()) {
			Location loc = new Location("kalman");
			loc.setLatitude(kalmanFilter.getLatitude());
			loc.setLongitude(kalmanFilter.getLongitude());
			loc.setAccuracy(kalmanFilter.getAccuracy());
			loc.setTime(System.currentTimeMillis());
			return loc;
		}

		Location bestLocation = currentUserLocation;
		LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
		if (lm != null) {
			List<String> providers = lm.getProviders(true);
			for (String provider : providers) {
				try {
					Location loc = lm.getLastKnownLocation(provider);
					if (loc != null && (bestLocation == null || loc.getTime() > bestLocation.getTime())) {
						bestLocation = loc;
					}
				} catch (SecurityException ignored) {
				}
			}
		}
		return bestLocation;
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

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				NotificationChannel channel = new NotificationChannel(
						CHANNEL_ID,
						context.getString(R.string.car_finder_title),
						NotificationManager.IMPORTANCE_HIGH);
				channel.setDescription(context.getString(R.string.car_finder_notif_desc));
				channel.enableLights(true);
				nm.createNotificationChannel(channel);
			}

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
		userLocationListeners.add(listener);
		if (isTrackingLocation) {
			if (currentUserLocation != null) listener.onUserLocationChanged(currentUserLocation);
			return;
		}

		Context app = context.getApplicationContext();
		startLocationTrackingInternal(app);
	}

	private synchronized void startLocationTrackingInternal(Context app) {
		if (isTrackingLocation) return;
		if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return;
		}

		LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
		if (lm == null) return;

		if (currentUserLocation == null) {
			Location seed = queryBestKnownUserLocation(app);
			if (seed != null) {
				kalmanFilter.update(seed.getLatitude(), seed.getLongitude(),
						seed.hasAccuracy() ? seed.getAccuracy() : 20.0f,
						seed.hasSpeed() ? seed.getSpeed() : -1.0f,
						seed.getTime());
				smoother.recordLocationSample(seed);
			}
		}

		activeLocationListener = new LocationListener() {
			@Override public void onLocationChanged(@NonNull Location location) { handleTrackedLocation(location); }
			@Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
			@Override public void onProviderEnabled(@NonNull String provider) {}
			@Override public void onProviderDisabled(@NonNull String provider) {}
		};
		networkLocationListener = new LocationListener() {
			@Override public void onLocationChanged(@NonNull Location location) { handleTrackedLocation(location); }
			@Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
			@Override public void onProviderEnabled(@NonNull String provider) {}
			@Override public void onProviderDisabled(@NonNull String provider) {}
		};
		fusedLocationListener = new LocationListener() {
			@Override public void onLocationChanged(@NonNull Location location) { handleTrackedLocation(location); }
			@Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
			@Override public void onProviderEnabled(@NonNull String provider) {}
			@Override public void onProviderDisabled(@NonNull String provider) {}
		};
		passiveLocationListener = new LocationListener() {
			@Override public void onLocationChanged(@NonNull Location location) { handleTrackedLocation(location); }
			@Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
			@Override public void onProviderEnabled(@NonNull String provider) {}
			@Override public void onProviderDisabled(@NonNull String provider) {}
		};

		try {
			if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.0f, activeLocationListener, Looper.getMainLooper());
			}
			if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
				lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0.0f, networkLocationListener, Looper.getMainLooper());
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				try {
					if (lm.getProvider(LocationManager.FUSED_PROVIDER) != null) {
						lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 1000L, 0.0f, fusedLocationListener, Looper.getMainLooper());
					}
				} catch (Throwable ignored) {}
			}
			try {
				if (lm.getProvider(LocationManager.PASSIVE_PROVIDER) != null) {
					lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000L, 0.0f, passiveLocationListener, Looper.getMainLooper());
				}
			} catch (Throwable ignored) {}
			isTrackingLocation = true;
			Log.i("CarFinder: Started multi-source location tracking (vehicular=", isTrackingVehicularLocation, ")");
		} catch (SecurityException e) {
			Log.w(e, "CarFinder: Location permission missing during update request");
		}
	}

	private void handleTrackedLocation(@NonNull Location location) {
		currentUserLocation = location;
		kalmanFilter.update(location.getLatitude(), location.getLongitude(),
				location.hasAccuracy() ? location.getAccuracy() : 25.0f,
				location.hasSpeed() ? location.getSpeed() : -1.0f,
				location.getTime());
		smoother.recordLocationSample(location);
		for (UserLocationListener l : userLocationListeners) {
			l.onUserLocationChanged(location);
		}
	}

	public synchronized void stopTrackingUserLocation(UserLocationListener listener) {
		userLocationListeners.remove(listener);
		if (userLocationListeners.isEmpty() && !isTrackingVehicularLocation) {
			Context app = FermataApplication.get();
			stopLocationTrackingInternal(app);
		}
	}

	private synchronized void stopLocationTrackingInternal(Context app) {
		if (!isTrackingLocation) return;
		LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
		if (lm != null) {
			try { if (activeLocationListener != null) lm.removeUpdates(activeLocationListener); } catch (SecurityException ignored) {}
			try { if (networkLocationListener != null) lm.removeUpdates(networkLocationListener); } catch (SecurityException ignored) {}
			try { if (fusedLocationListener != null) lm.removeUpdates(fusedLocationListener); } catch (SecurityException ignored) {}
			try { if (passiveLocationListener != null) lm.removeUpdates(passiveLocationListener); } catch (SecurityException ignored) {}
		}
		activeLocationListener = networkLocationListener = fusedLocationListener = passiveLocationListener = null;
		isTrackingLocation = false;
		Log.i("CarFinder: Stopped location tracking");
	}
}
