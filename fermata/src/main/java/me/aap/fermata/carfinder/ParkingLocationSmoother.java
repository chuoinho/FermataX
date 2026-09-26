package me.aap.fermata.carfinder;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import me.aap.utils.log.Log;

/**
 * Sliding buffer and Retroactive Anchor smoother for parked vehicle estimation.
 * Collects GNSS fixes before and after disconnect, anchoring strictly to the vehicular
 * fix at T0 (disconnect time) to eliminate pedestrian walking drift.
 * Detects basement parking (GPS signal outage) and anchors to last reliable ramp/entrance fix.
 */
public final class ParkingLocationSmoother {
	private static final int MAX_BUFFER_SIZE = 60;
	private static final long MAX_BUFFER_AGE_MS = 120_000L; // 2 minutes

	public static final class SmoothedResult {
		public final double latitude;
		public final double longitude;
		public final float accuracy;
		public final float uncertainty;
		public final String confidenceLevel; // "high", "medium", "basement", "low"
		public final long timestamp;

		public SmoothedResult(double latitude, double longitude, float accuracy, float uncertainty,
				String confidenceLevel, long timestamp) {
			this.latitude = latitude;
			this.longitude = longitude;
			this.accuracy = accuracy;
			this.uncertainty = uncertainty;
			this.confidenceLevel = confidenceLevel;
			this.timestamp = timestamp;
		}
	}

	private final LinkedList<LocationFix> buffer = new LinkedList<>();

	/**
	 * Records an incoming GNSS location sample into the sliding buffer.
	 */
	public synchronized void recordLocationSample(@Nullable Location loc) {
		if (loc == null) return;
		long now = System.currentTimeMillis();
		buffer.addLast(new LocationFix(
				loc.getLatitude(),
				loc.getLongitude(),
				loc.hasAccuracy() ? loc.getAccuracy() : 25.0f,
				loc.hasSpeed() ? loc.getSpeed() : 0.0f,
				loc.getTime() > 0 ? loc.getTime() : now
		));

		// Purge stale entries
		while (buffer.size() > MAX_BUFFER_SIZE || (!buffer.isEmpty() && buffer.getFirst().time < now - MAX_BUFFER_AGE_MS)) {
			buffer.removeFirst();
		}
	}

	/** Backwards-compatible alias */
	public synchronized void addLocation(@Nullable Location loc) {
		recordLocationSample(loc);
	}

	/** Clears all buffered location fixes */
	public synchronized void clear() {
		buffer.clear();
	}

	/**
	 * Returns an immutable snapshot copy of recent location fixes.
	 */
	public synchronized List<LocationFix> getLocationFixHistory() {
		return new ArrayList<>(buffer);
	}

	/** Backwards-compatible alias */
	public synchronized List<LocationFix> getSnapshot() {
		return getLocationFixHistory();
	}

	/**
	 * Estimates the parked location around anchorTime using Retroactive Anchor at T0.
	 */
	@NonNull
	public synchronized SmoothedResult estimateParkedLocation(long anchorTime, @Nullable Location fallbackLoc) {
		List<LocationFix> fixes = getLocationFixHistory();
		if (fixes.isEmpty()) {
			if (fallbackLoc != null) {
				float acc = fallbackLoc.hasAccuracy() ? fallbackLoc.getAccuracy() : 25.0f;
				return new SmoothedResult(fallbackLoc.getLatitude(), fallbackLoc.getLongitude(), acc, acc, "medium", anchorTime);
			}
			return new SmoothedResult(0.0, 0.0, 50.0f, 50.0f, "low", anchorTime);
		}

		// Partition fixes into pre-anchor (vehicular) and post-anchor (pedestrian walking)
		List<LocationFix> preFixes = new ArrayList<>();
		List<LocationFix> postFixes = new ArrayList<>();
		for (LocationFix f : fixes) {
			if (f.time <= anchorTime + 2000L) { // 2s grace window
				preFixes.add(f);
			} else {
				postFixes.add(f);
			}
		}

		// Check for basement GPS signal loss
		SmoothedResult basementResult = detectBasementParkingGPSOutage(preFixes, postFixes);
		if (basementResult != null) {
			return basementResult;
		}

		// Retroactive Anchor at T0: select best stationary vehicular fix right at disconnect
		LocationFix bestCandidate = findBestVehicularAnchorCandidate(preFixes, anchorTime, fallbackLoc, fixes);

		float uncertainty = bestCandidate.accuracy;
		String conf = classifyConfidenceLevel(uncertainty);

		Log.i("CarFinder Smoother: Retroactive T0 anchor lat=", bestCandidate.latitude,
				" lng=", bestCandidate.longitude, " acc=", bestCandidate.accuracy, " conf=", conf);
		return new SmoothedResult(bestCandidate.latitude, bestCandidate.longitude,
				bestCandidate.accuracy, uncertainty, conf, anchorTime);
	}

	@Nullable
	private SmoothedResult detectBasementParkingGPSOutage(List<LocationFix> preFixes, List<LocationFix> postFixes) {
		boolean hadGoodPreSignal = false;
		LocationFix lastGoodPreFix = null;
		for (LocationFix f : preFixes) {
			if (f.accuracy <= 30.0f) {
				hadGoodPreSignal = true;
				if (lastGoodPreFix == null || f.time >= lastGoodPreFix.time) {
					lastGoodPreFix = f;
				}
			}
		}

		boolean postDegradedOrMissing = true;
		if (!postFixes.isEmpty()) {
			for (LocationFix f : postFixes) {
				if (f.accuracy <= 40.0f) {
					postDegradedOrMissing = false;
					break;
				}
			}
		}

		if (hadGoodPreSignal && lastGoodPreFix != null && postDegradedOrMissing) {
			Log.i("CarFinder Smoother: Basement GPS outage detected! Anchoring to entrance at: ",
					lastGoodPreFix.latitude, ", ", lastGoodPreFix.longitude);
			return new SmoothedResult(
					lastGoodPreFix.latitude,
					lastGoodPreFix.longitude,
					lastGoodPreFix.accuracy,
					Math.max(lastGoodPreFix.accuracy, 20.0f),
					"basement",
					lastGoodPreFix.time);
		}
		return null;
	}

	@NonNull
	private LocationFix findBestVehicularAnchorCandidate(List<LocationFix> preFixes, long anchorTime,
			@Nullable Location fallbackLoc, List<LocationFix> allFixes) {
		LocationFix bestCandidate = null;
		float bestScore = Float.MAX_VALUE;

		for (LocationFix f : preFixes) {
			long dtMs = anchorTime - f.time;
			if (dtMs < -2000L || dtMs > 25_000L) continue; // Window: [T0 - 25s, T0 + 2s]

			// Score candidate: lower is better.
			// Favor stationary fixes (speed <= 1.5 m/s) and fixes closest to T0 with best accuracy.
			float speedPenalty = (f.speed > 1.5f) ? (f.speed * 2.0f) : 0.0f;
			float timePenalty = (float) (Math.abs(dtMs) / 1000.0) * 0.4f;
			float score = f.accuracy + speedPenalty + timePenalty;

			if (score < bestScore) {
				bestScore = score;
				bestCandidate = f;
			}
		}

		if (bestCandidate == null && fallbackLoc != null) {
			float acc = fallbackLoc.hasAccuracy() ? fallbackLoc.getAccuracy() : 25.0f;
			bestCandidate = new LocationFix(
					fallbackLoc.getLatitude(),
					fallbackLoc.getLongitude(),
					acc,
					fallbackLoc.hasSpeed() ? fallbackLoc.getSpeed() : 0.0f,
					anchorTime);
		}

		if (bestCandidate == null) {
			bestCandidate = allFixes.get(allFixes.size() - 1);
		}
		return bestCandidate;
	}

	private String classifyConfidenceLevel(float uncertainty) {
		if (uncertainty <= 12.0f) {
			return "high";
		} else if (uncertainty <= 30.0f) {
			return "medium";
		} else {
			return "low";
		}
	}
}
