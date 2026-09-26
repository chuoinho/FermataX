package me.aap.fermata.carfinder;

/**
 * Computes a rolling circular mean over 40 samples to calculate
 * compass drift or heading offset while walking.
 */
public final class CircularMeanFilter {
	private static final int CAPACITY = 40;
	private final double[] sinSum = new double[CAPACITY];
	private final double[] cosSum = new double[CAPACITY];
	private int count = 0;
	private int writeIndex = 0;

	public synchronized void reset() {
		count = 0;
		writeIndex = 0;
	}

	public synchronized void addSample(float angleDegrees) {
		double radians = Math.toRadians(angleDegrees);
		sinSum[writeIndex] = Math.sin(radians);
		cosSum[writeIndex] = Math.cos(radians);
		writeIndex = (writeIndex + 1) % CAPACITY;
		if (count < CAPACITY) count++;
	}

	/**
	 * Returns the circular mean offset in degrees, or Float.NaN if confidence is too low.
	 */
	public synchronized float getMeanOffset() {
		if (count < 12) return Float.NaN;

		double sumSin = 0.0;
		double sumCos = 0.0;
		for (int i = 0; i < count; i++) {
			sumSin += sinSum[i];
			sumCos += cosSum[i];
		}

		double r = Math.hypot(sumSin, sumCos) / count;
		if (r < 0.75) return Float.NaN; // Consistency too low

		float meanDeg = AngleUtils.normalizeAngle((float) Math.toDegrees(Math.atan2(sumSin, sumCos)));
		if (Math.abs(meanDeg) > 60.0f) return Float.NaN;

		return meanDeg;
	}
}
