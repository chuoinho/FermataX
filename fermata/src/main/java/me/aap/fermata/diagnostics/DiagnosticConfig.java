package me.aap.fermata.diagnostics;

import java.util.concurrent.TimeUnit;

/** Immutable resource limits for the local diagnostics journal. */
public final class DiagnosticConfig {
	public static final long DEFAULT_MAX_TOTAL_BYTES = 8L * 1024L * 1024L;
	public static final long DEFAULT_MAX_FILE_BYTES = 512L * 1024L;
	public static final long DEFAULT_MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(7);
	public static final int DEFAULT_QUEUE_CAPACITY = 512;
	public static final long DEFAULT_MAX_QUEUE_BYTES = 512L * 1024L;
	public static final int DEFAULT_BREADCRUMB_CAPACITY = 200;
	public static final int DEFAULT_MAX_STRING_CHARS = 8192;
	public static final long DEFAULT_FLUSH_INTERVAL_MILLIS = 1000L;

	private final long maxTotalBytes;
	private final long maxFileBytes;
	private final long maxAgeMillis;
	private final int queueCapacity;
	private final long maxQueueBytes;
	private final int breadcrumbCapacity;
	private final int maxStringChars;
	private final long flushIntervalMillis;

	private DiagnosticConfig(Builder builder) {
		maxTotalBytes = positive(builder.maxTotalBytes, "maxTotalBytes");
		maxFileBytes = positive(builder.maxFileBytes, "maxFileBytes");
		maxAgeMillis = positive(builder.maxAgeMillis, "maxAgeMillis");
		queueCapacity = positive(builder.queueCapacity, "queueCapacity");
		maxQueueBytes = positive(builder.maxQueueBytes, "maxQueueBytes");
		breadcrumbCapacity = positive(builder.breadcrumbCapacity, "breadcrumbCapacity");
		maxStringChars = positive(builder.maxStringChars, "maxStringChars");
		flushIntervalMillis = positive(builder.flushIntervalMillis, "flushIntervalMillis");
		if (maxFileBytes > maxTotalBytes) {
			throw new IllegalArgumentException("maxFileBytes must not exceed maxTotalBytes");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static DiagnosticConfig defaults() {
		return builder().build();
	}

	public long getMaxTotalBytes() {
		return maxTotalBytes;
	}

	public long getMaxFileBytes() {
		return maxFileBytes;
	}

	public long getMaxAgeMillis() {
		return maxAgeMillis;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public long getMaxQueueBytes() {
		return maxQueueBytes;
	}

	public int getBreadcrumbCapacity() {
		return breadcrumbCapacity;
	}

	public int getMaxStringChars() {
		return maxStringChars;
	}

	public long getFlushIntervalMillis() {
		return flushIntervalMillis;
	}

	private static int positive(int value, String name) {
		if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
		return value;
	}

	private static long positive(long value, String name) {
		if (value <= 0L) throw new IllegalArgumentException(name + " must be positive");
		return value;
	}

	public static final class Builder {
		private long maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;
		private long maxFileBytes = DEFAULT_MAX_FILE_BYTES;
		private long maxAgeMillis = DEFAULT_MAX_AGE_MILLIS;
		private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
		private long maxQueueBytes = DEFAULT_MAX_QUEUE_BYTES;
		private int breadcrumbCapacity = DEFAULT_BREADCRUMB_CAPACITY;
		private int maxStringChars = DEFAULT_MAX_STRING_CHARS;
		private long flushIntervalMillis = DEFAULT_FLUSH_INTERVAL_MILLIS;

		public Builder maxTotalBytes(long value) {
			maxTotalBytes = value;
			return this;
		}

		public Builder maxFileBytes(long value) {
			maxFileBytes = value;
			return this;
		}

		public Builder maxAgeMillis(long value) {
			maxAgeMillis = value;
			return this;
		}

		public Builder queueCapacity(int value) {
			queueCapacity = value;
			return this;
		}

		public Builder maxQueueBytes(long value) {
			maxQueueBytes = value;
			return this;
		}

		public Builder breadcrumbCapacity(int value) {
			breadcrumbCapacity = value;
			return this;
		}

		public Builder maxStringChars(int value) {
			maxStringChars = value;
			return this;
		}

		public Builder flushIntervalMillis(long value) {
			flushIntervalMillis = value;
			return this;
		}

		public DiagnosticConfig build() {
			return new DiagnosticConfig(this);
		}
	}
}
