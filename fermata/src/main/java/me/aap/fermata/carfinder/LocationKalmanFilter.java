package me.aap.fermata.carfinder;

/**
 * 2D Kalman filter to smooth and denoise GPS coordinate readings,
 * estimating true position and dynamic accuracy variance.
 */
public final class LocationKalmanFilter {
	private double lat;
	private double lng;
	private float variance = -1.0f; // -1 indicates uninitialized
	private long timestamp;

	public synchronized void reset() {
		variance = -1.0f;
		timestamp = 0L;
	}

	public synchronized boolean isInitialized() {
		return variance >= 0.0f;
	}

	public synchronized double getLatitude() {
		return lat;
	}

	public synchronized double getLongitude() {
		return lng;
	}

	public synchronized float getAccuracy() {
		return variance < 0.0f ? -1.0f : (float) Math.sqrt(variance);
	}

	/**
	 * Updates the filter with a new raw GPS observation.
	 *
	 * @param rawLat      raw observed latitude in degrees
	 * @param rawLng      raw observed longitude in degrees
	 * @param rawAccuracy observed accuracy radius in meters
	 * @param speed       speed in m/s (or negative if unknown)
	 * @param time        timestamp in milliseconds
	 */
	public synchronized void update(double rawLat, double rawLng, float rawAccuracy, float speed, long time) {
		float accuracy = Math.max(1.0f, rawAccuracy);
		if (variance < 0.0f) {
			this.lat = rawLat;
			this.lng = rawLng;
			this.variance = accuracy * accuracy;
			this.timestamp = time;
			return;
		}

		float estimatedSpeed;
		if (speed < 0.0f) {
			long dt = time - this.timestamp;
			if (dt > 0) {
				double metersX = Math.cos(Math.toRadians(this.lat)) * ((rawLng - this.lng) * 111320.0);
				double metersY = (rawLat - this.lat) * 111320.0;
				estimatedSpeed = (float) (Math.hypot(metersX, metersY) / (dt / 1000.0));
			} else {
				estimatedSpeed = 0.0f;
			}
		} else {
			estimatedSpeed = speed;
		}

		float speedNoise = (estimatedSpeed < 0.6f) ? 0.0f : estimatedSpeed;
		float processNoise = Math.max(0.3f, Math.min(30.0f, speedNoise * 3.0f));

		long dt = time - this.timestamp;
		if (dt > 0) {
			this.variance += (dt * processNoise * processNoise) / 1000.0f;
			this.timestamp = time;
		}

		float measurementVariance = accuracy * accuracy;
		float kalmanGain = this.variance / (measurementVariance + this.variance);

		this.lat += kalmanGain * (rawLat - this.lat);
		this.lng += kalmanGain * (rawLng - this.lng);
		this.variance = (1.0f - kalmanGain) * this.variance;
	}
}
