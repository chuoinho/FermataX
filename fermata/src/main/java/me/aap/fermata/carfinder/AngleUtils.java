package me.aap.fermata.carfinder;

import android.location.Location;

/**
 * Geometric and angular utility functions for navigation calculations.
 */
public final class AngleUtils {
	private AngleUtils() {
	}

	/**
	 * Normalizes an angle into the [-180, 180) range.
	 */
	public static float normalizeAngle(float degrees) {
		float angle = degrees % 360.0f;
		if (angle >= 180.0f) angle -= 360.0f;
		if (angle < -180.0f) angle += 360.0f;
		return angle;
	}

	/**
	 * Computes distance in meters between two coordinates.
	 */
	public static float distanceBetween(double lat1, double lon1, double lat2, double lon2) {
		float[] results = new float[1];
		Location.distanceBetween(lat1, lon1, lat2, lon2, results);
		return results[0];
	}

	/**
	 * Computes initial bearing (0° = North, clockwise) from start to destination.
	 */
	public static float bearingBetween(double lat1, double lon1, double lat2, double lon2) {
		float[] results = new float[2];
		Location.distanceBetween(lat1, lon1, lat2, lon2, results);
		return results.length > 1 ? results[1] : 0.0f;
	}

	/**
	 * Estimates walking time in minutes based on average pedestrian speed 1.25 m/s (4.5 km/h).
	 */
	public static int estimateWalkingMinutes(float distanceMeters) {
		if (distanceMeters <= 0.0f) return 0;
		return Math.max(1, Math.round((distanceMeters / 1.25f) / 60.0f));
	}
}
