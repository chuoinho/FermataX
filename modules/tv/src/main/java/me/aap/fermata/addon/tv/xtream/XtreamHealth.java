package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author Andrey Pavlenko
 */
public class XtreamHealth {
	private final XtreamAccount account;
	private XtreamStatus status;
	private int liveCategories;
	private int vodCategories;
	private int seriesCategories;
	private int testedStreamId;
	private String testedStreamName;
	private int streamStatusCode;

	public XtreamHealth(XtreamAccount account) {
		this.account = account;
	}

	public XtreamStatus getStatus() {
		return status;
	}

	public void setStatus(XtreamStatus status) {
		this.status = status;
	}

	public int getLiveCategories() {
		return liveCategories;
	}

	public int getVodCategories() {
		return vodCategories;
	}

	public int getSeriesCategories() {
		return seriesCategories;
	}

	public void setCategoryCounts(int liveCategories, int vodCategories, int seriesCategories) {
		this.liveCategories = liveCategories;
		this.vodCategories = vodCategories;
		this.seriesCategories = seriesCategories;
	}

	public int getTotalCategories() {
		return liveCategories + vodCategories + seriesCategories;
	}

	public int getTestedStreamId() {
		return testedStreamId;
	}

	public String getTestedStreamName() {
		return testedStreamName;
	}

	public void setTestedStream(XtreamChannel channel) {
		testedStreamId = channel.getStreamId();
		testedStreamName = channel.getName();
	}

	public int getStreamStatusCode() {
		return streamStatusCode;
	}

	public void setStreamStatusCode(int streamStatusCode) {
		this.streamStatusCode = streamStatusCode;
	}

	public IOException accountFailure() {
		XtreamStatus s = status;
		if (s == null) return new IOException("Xtream health check failed.");

		if (s.isExpired()) {
			String expiry = formatExpiry(s.getExpiryTime());
			return new IOException(isNullOrBlank(expiry) ? "Xtream account is expired." :
					"Xtream account expired on " + expiry + ".");
		}

		if (!s.hasFreeConnectionSlot()) {
			return new IOException("Xtream account has no free connection slots (active/max: " +
					s.getActiveConnections() + "/" + s.getMaxConnections() +
					"). Stop another stream or increase max connections.");
		}

		if (!s.isAuthenticated()) {
			String msg = s.getMessage();
			if (isNullOrBlank(msg)) msg = s.getStatus();
			return new IOException(isNullOrBlank(msg) ?
					"Xtream authentication failed. Check username and password." :
					"Xtream authentication failed: " + msg);
		}

		if (!s.isActive()) {
			String msg = s.getMessage();
			if (isNullOrBlank(msg)) msg = s.getStatus();
			return new IOException(isNullOrBlank(msg) ? "Xtream account is not active." :
					"Xtream account is not active: " + msg);
		}

		return null;
	}

	public IOException noCategoriesFailure() {
		return new IOException("Xtream account has no Live TV, Movies, or Series categories.");
	}

	public IOException noLiveStreamsFailure() {
		return new IOException("Xtream live categories are available, but no testable live stream was returned.");
	}

	public IOException streamFailure(int status, String reason) {
		String stream = isNullOrBlank(testedStreamName) ? String.valueOf(testedStreamId) :
				testedStreamName;
		String suffix = isNullOrBlank(reason) ? "" : " " + reason;

		if ((status == 401) || (status == 403)) {
			return new IOException("Xtream stream test failed: stream was rejected (HTTP " + status +
					suffix + "). Check account expiry, credentials, and free connection slots.");
		} else if (status == 404) {
			return new IOException("Xtream stream test failed: stream is dead or missing (HTTP 404): " +
					stream + ".");
		} else if (status >= 500) {
			return new IOException("Xtream stream test failed: server error (HTTP " + status + suffix +
					") while probing " + stream + ".");
		}

		return new IOException("Xtream stream test failed (HTTP " + status + suffix +
				") while probing " + stream + ".");
	}

	private String formatExpiry(long expiryTime) {
		if (expiryTime <= 0) return null;
		return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(expiryTime * 1000L));
	}
}
