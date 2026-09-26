package me.aap.fermata.carfinder;

/**
 * Immutable snapshot of a GNSS or fused location measurement stored in the sliding buffer.
 */
public final class LocationFix {
	public final double latitude;
	public final double longitude;
	public final float accuracy;
	public final float speed; // m/s
	public final long time; // ms

	public LocationFix(double latitude, double longitude, float accuracy, float speed, long time) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracy = accuracy;
		this.speed = speed;
		this.time = time;
	}
}
