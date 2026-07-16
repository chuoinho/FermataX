package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamStatus {
	private final boolean authenticated;
	private final String status;
	private final String message;
	private final long expiryTime;
	private final int activeConnections;
	private final int maxConnections;

	public XtreamStatus(boolean authenticated, String status, String message) {
		this(authenticated, status, message, 0, -1, -1);
	}

	public XtreamStatus(boolean authenticated, String status, String message, long expiryTime,
										 int activeConnections, int maxConnections) {
		this.authenticated = authenticated;
		this.status = status;
		this.message = message;
		this.expiryTime = expiryTime;
		this.activeConnections = activeConnections;
		this.maxConnections = maxConnections;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public boolean isActive() {
		return authenticated && ((status == null) || "active".equalsIgnoreCase(status));
	}

	public boolean isExpired() {
		return "expired".equalsIgnoreCase(status) ||
				((expiryTime > 0) && (expiryTime * 1000L < System.currentTimeMillis()));
	}

	public boolean hasFreeConnectionSlot() {
		return (maxConnections <= 0) || (activeConnections < 0) ||
				(activeConnections < maxConnections);
	}

	public String getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public long getExpiryTime() {
		return expiryTime;
	}

	public int getActiveConnections() {
		return activeConnections;
	}

	public int getMaxConnections() {
		return maxConnections;
	}
}
