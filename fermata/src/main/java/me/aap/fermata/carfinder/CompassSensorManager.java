package me.aap.fermata.carfinder;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.view.Surface;
import android.view.WindowManager;

/**
 * High-precision heading and orientation manager.
 * Implements device rotation remapping, gimbal-lock tilt suppression,
 * exponential sin/cos low-pass filtering, magnetic declination correction,
 * and GPS walking circular-mean drift compensation ported from decompiled APK.
 */
public final class CompassSensorManager implements SensorEventListener {
	public interface HeadingListener {
		void onHeadingChanged(float trueAzimuthDegrees, boolean isCalibrated);
	}

	private final Context context;
	private final SensorManager sensorManager;
	private final Sensor rotationVectorSensor;
	private final Sensor accelerometerSensor;
	private final Sensor magneticSensor;
	private final HeadingListener listener;

	private final float[] baseMatrix = new float[9];
	private final float[] remappedMatrix = new float[9];
	private float[] accelerometerReading;
	private float[] magneticReading;

	// Sin/Cos low-pass filter states
	private float filteredSin = 0f;
	private float filteredCos = 0f;
	private boolean filterInitialized = false;

	// Corrections
	private float magneticDeclination = 0f;
	private final CircularMeanFilter circularMeanFilter = new CircularMeanFilter();

	private boolean isRunning;
	private boolean isUnreliableAccuracy;

	public CompassSensorManager(Context context, HeadingListener listener) {
		this.context = context;
		this.listener = listener;
		Context app = context.getApplicationContext();
		SensorManager sm = null;
		try {
			sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
		} catch (Throwable ignored) {
		}
		this.sensorManager = sm;

		Sensor rot = null;
		Sensor acc = null;
		Sensor mag = null;
		if (sm != null) {
			rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
			acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		}
		this.rotationVectorSensor = rot;
		this.accelerometerSensor = acc;
		this.magneticSensor = mag;
	}

	public boolean hasCompassSensor() {
		return rotationVectorSensor != null || (accelerometerSensor != null && magneticSensor != null);
	}

	public synchronized void start() {
		if (isRunning || sensorManager == null) return;
		isRunning = true;
		filterInitialized = false;

		try {
			if (rotationVectorSensor != null) {
				sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
			} else if (accelerometerSensor != null && magneticSensor != null) {
				sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
				sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
			}
		} catch (Throwable ignored) {
		}
	}

	public synchronized void stop() {
		if (!isRunning || sensorManager == null) return;
		isRunning = false;
		try {
			sensorManager.unregisterListener(this);
		} catch (Throwable ignored) {
		}
		filterInitialized = false;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public boolean isUnreliable() {
		return isUnreliableAccuracy;
	}

	/**
	 * Updates the current user location to calculate geomagnetic declination and GPS drift compensation.
	 */
	public void updateLocation(Location location) {
		if (location == null) return;

		try {
			GeomagneticField field = new GeomagneticField(
					(float) location.getLatitude(),
					(float) location.getLongitude(),
					(float) location.getAltitude(),
					System.currentTimeMillis()
			);
			magneticDeclination = field.getDeclination();
		} catch (Throwable ignored) {
		}

		// When walking at speed >= 0.8 m/s, GPS bearing provides ground truth trajectory
		if (location.hasBearing() && location.hasSpeed() && location.getSpeed() >= 0.8f && filterInitialized) {
			float currentCompensated = getCurrentComputedAzimuth();
			if (!Float.isNaN(currentCompensated)) {
				float drift = AngleUtils.normalizeAngle(location.getBearing() - currentCompensated);
				circularMeanFilter.addSample(drift);
			}
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (!isRunning || event == null || event.values == null) return;

		int type = event.sensor.getType();
		boolean hasMatrix = false;

		if (type == Sensor.TYPE_ROTATION_VECTOR) {
			float[] vec = event.values;
			if (vec.length > 4) {
				float[] truncated = new float[4];
				System.arraycopy(vec, 0, truncated, 0, 4);
				vec = truncated;
			}
			SensorManager.getRotationMatrixFromVector(baseMatrix, vec);
			hasMatrix = true;
		} else if (type == Sensor.TYPE_ACCELEROMETER) {
			accelerometerReading = event.values.clone();
			if (magneticReading != null) {
				hasMatrix = SensorManager.getRotationMatrix(baseMatrix, null, accelerometerReading, magneticReading);
			}
		} else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
			magneticReading = event.values.clone();
			if (accelerometerReading != null) {
				hasMatrix = SensorManager.getRotationMatrix(baseMatrix, null, accelerometerReading, magneticReading);
			}
		}

		if (!hasMatrix) return;

		// Remap coordinate system based on current device screen orientation
		remapCoordinateSystem(baseMatrix, remappedMatrix);

		// Tilt check from decompiled e6.java: hypot(R[1], R[4])
		float r1 = remappedMatrix[1];
		float r4 = remappedMatrix[4];
		double hypot = Math.hypot(r1, r4);
		if (hypot < 0.12) {
			// Device is pointing almost straight up or straight down; skip update to prevent wild needle spinning
			return;
		}

		double rawAzimuth = Math.toDegrees(Math.atan2(r1, r4));
		rawAzimuth = (rawAzimuth + 360.0) % 360.0;

		// Exponential filter on sin and cos (alpha = 0.15)
		double rad = Math.toRadians(rawAzimuth);
		float sin = (float) Math.sin(rad);
		float cos = (float) Math.cos(rad);

		if (!filterInitialized) {
			filteredSin = sin;
			filteredCos = cos;
			filterInitialized = true;
		} else {
			filteredSin += 0.15f * (sin - filteredSin);
			filteredCos += 0.15f * (cos - filteredCos);
		}

		float computedHeading = getCurrentComputedAzimuth();
		if (!Float.isNaN(computedHeading) && listener != null) {
			listener.onHeadingChanged(computedHeading, !isUnreliableAccuracy);
		}
	}

	private float getCurrentComputedAzimuth() {
		if (!filterInitialized) return Float.NaN;

		float base = (float) ((Math.toDegrees(Math.atan2(filteredSin, filteredCos)) + 360.0) % 360.0);
		// Add geomagnetic declination (Magnetic North -> True North)
		base = (base + magneticDeclination + 360.0f) % 360.0f;

		// Add GPS walking drift compensation if available
		float drift = circularMeanFilter.getMeanOffset();
		if (!Float.isNaN(drift)) {
			base = (base + drift + 360.0f) % 360.0f;
		}

		return base;
	}

	private void remapCoordinateSystem(float[] inR, float[] outR) {
		int rotation = Surface.ROTATION_0;
		try {
			WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			if (wm != null && wm.getDefaultDisplay() != null) {
				rotation = wm.getDefaultDisplay().getRotation();
			}
		} catch (Throwable ignored) {
		}

		int axisX;
		int axisY;
		switch (rotation) {
			case Surface.ROTATION_90:
				axisX = SensorManager.AXIS_Y;
				axisY = SensorManager.AXIS_MINUS_X;
				break;
			case Surface.ROTATION_180:
				axisX = SensorManager.AXIS_MINUS_X;
				axisY = SensorManager.AXIS_MINUS_Y;
				break;
			case Surface.ROTATION_270:
				axisX = SensorManager.AXIS_MINUS_Y;
				axisY = SensorManager.AXIS_X;
				break;
			case Surface.ROTATION_0:
			default:
				axisX = SensorManager.AXIS_X;
				axisY = SensorManager.AXIS_Y;
				break;
		}

		SensorManager.remapCoordinateSystem(inR, axisX, axisY, outR);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		if (sensor == null) return;
		int type = sensor.getType();
		if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_MAGNETIC_FIELD) {
			isUnreliableAccuracy = (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
					accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW);
		}
	}
}
