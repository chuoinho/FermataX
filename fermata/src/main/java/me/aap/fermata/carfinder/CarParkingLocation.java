package me.aap.fermata.carfinder;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Encapsulates the vehicle's last recorded parking position, including
 * posterior uncertainty and confidence classification ("high", "medium", "basement", "manual").
 */
public final class CarParkingLocation implements Serializable {
	private final double latitude;
	private final double longitude;
	private final float accuracy;
	private final float uncertainty;
	private final String confidenceLevel;
	private final long timestamp;
	private final String address;
	private String note;
	private String photoPath;

	public CarParkingLocation(double latitude, double longitude, float accuracy, long timestamp) {
		this(latitude, longitude, accuracy, accuracy, "high", timestamp, "", "", "");
	}

	public CarParkingLocation(double latitude, double longitude, float accuracy, long timestamp,
			@Nullable String address, @Nullable String note, @Nullable String photoPath) {
		this(latitude, longitude, accuracy, accuracy, "high", timestamp, address, note, photoPath);
	}

	public CarParkingLocation(double latitude, double longitude, float accuracy, float uncertainty,
			@Nullable String confidenceLevel, long timestamp,
			@Nullable String address, @Nullable String note, @Nullable String photoPath) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.accuracy = accuracy;
		this.uncertainty = (uncertainty > 0f) ? uncertainty : accuracy;
		this.confidenceLevel = (confidenceLevel != null && !confidenceLevel.isEmpty()) ? confidenceLevel : "high";
		this.timestamp = timestamp;
		this.address = (address != null) ? address : "";
		this.note = (note != null) ? note : "";
		this.photoPath = (photoPath != null) ? photoPath : "";
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public float getAccuracy() {
		return accuracy;
	}

	public float getUncertainty() {
		return uncertainty;
	}

	public String getConfidenceLevel() {
		return confidenceLevel;
	}

	public boolean isBasement() {
		return "basement".equalsIgnoreCase(confidenceLevel);
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getAddress() {
		return address;
	}

	public String getNote() {
		return note;
	}

	public String getPhotoPath() {
		return photoPath;
	}

	public boolean isValid() {
		return timestamp > 0L && (latitude != 0.0 || longitude != 0.0);
	}

	public CarParkingLocation withAddress(String newAddress) {
		return new CarParkingLocation(latitude, longitude, accuracy, uncertainty, confidenceLevel, timestamp, newAddress, note, photoPath);
	}

	public CarParkingLocation withNote(String newNote) {
		return new CarParkingLocation(latitude, longitude, accuracy, uncertainty, confidenceLevel, timestamp, address, newNote, photoPath);
	}

	public CarParkingLocation withPhotoPath(String newPhotoPath) {
		return new CarParkingLocation(latitude, longitude, accuracy, uncertainty, confidenceLevel, timestamp, address, note, newPhotoPath);
	}

	public void setNote(@Nullable String note) {
		this.note = (note != null) ? note : "";
	}

	public void setPhotoPath(@Nullable String photoPath) {
		this.photoPath = (photoPath != null) ? photoPath : "";
	}

	public String toJson() {
		JSONObject obj = new JSONObject();
		try {
			obj.put("lat", latitude);
			obj.put("lng", longitude);
			obj.put("acc", (double) accuracy);
			obj.put("unc", (double) uncertainty);
			obj.put("conf", confidenceLevel);
			obj.put("time", timestamp);
			if (!address.isEmpty()) obj.put("addr", address);
			if (!note.isEmpty()) obj.put("note", note);
			if (!photoPath.isEmpty()) obj.put("photo", photoPath);
		} catch (JSONException ignored) {
		}
		return obj.toString();
	}

	@Nullable
	public static CarParkingLocation fromJson(@Nullable String json) {
		if (json == null || json.length() < 10) return null;
		try {
			JSONObject obj = new JSONObject(json);
			double lat = obj.optDouble("lat", 0.0);
			double lng = obj.optDouble("lng", 0.0);
			float acc = (float) obj.optDouble("acc", 0.0);
			float unc = (float) obj.optDouble("unc", acc);
			String conf = obj.optString("conf", "high");
			long time = obj.optLong("time", 0L);
			String addr = obj.optString("addr", "");
			String note = obj.optString("note", "");
			String photo = obj.optString("photo", "");
			CarParkingLocation loc = new CarParkingLocation(lat, lng, acc, unc, conf, time, addr, note, photo);
			return loc.isValid() ? loc : null;
		} catch (JSONException ignored) {
			return null;
		}
	}
}
