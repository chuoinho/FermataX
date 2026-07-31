package me.aap.fermata.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/** Immutable, Android-independent diagnostic event. */
public final class DiagnosticEvent {
	public static final int SCHEMA_VERSION = 1;

	private final long sequence;
	private final long wallTimeMillis;
	private final long elapsedRealtimeMillis;
	private final String sessionId;
	private final String operationId;
	private final String processName;
	private final int processId;
	private final String threadName;
	private final long threadId;
	private final DiagnosticScope scope;
	private final DiagnosticPriority priority;
	private final String category;
	private final String name;
	private final Map<String, Object> attributes;
	private volatile String encodedJson;
	private volatile int encodedByteLength = -1;

	private DiagnosticEvent(Builder builder) {
		this(0L, 0L, 0L, null, builder.operationId, null, 0, null, 0L,
				builder.scope, builder.priority, builder.category, builder.name,
				Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes)));
	}

	DiagnosticEvent(long sequence, long wallTimeMillis, long elapsedRealtimeMillis,
			String sessionId, String operationId, String processName, int processId,
			String threadName, long threadId, DiagnosticScope scope,
			DiagnosticPriority priority, String category, String name,
			Map<String, Object> attributes) {
		this.sequence = sequence;
		this.wallTimeMillis = wallTimeMillis;
		this.elapsedRealtimeMillis = elapsedRealtimeMillis;
		this.sessionId = sessionId;
		this.operationId = operationId;
		this.processName = processName;
		this.processId = processId;
		this.threadName = threadName;
		this.threadId = threadId;
		this.scope = scope;
		this.priority = priority;
		this.category = category;
		this.name = name;
		this.attributes = attributes;
	}

	public static Builder builder(String category, String name) {
		return new Builder(category, name);
	}

	public long getSequence() {
		return sequence;
	}

	public long getWallTimeMillis() {
		return wallTimeMillis;
	}

	public long getElapsedRealtimeMillis() {
		return elapsedRealtimeMillis;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getOperationId() {
		return operationId;
	}

	public String getProcessName() {
		return processName;
	}

	public int getProcessId() {
		return processId;
	}

	public String getThreadName() {
		return threadName;
	}

	public long getThreadId() {
		return threadId;
	}

	public DiagnosticScope getScope() {
		return scope;
	}

	public DiagnosticPriority getPriority() {
		return priority;
	}

	public String getCategory() {
		return category;
	}

	public String getName() {
		return name;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	int estimatedBytes() {
		int bytes = encodedByteLength;
		if (bytes < 0) encodedByteLength = bytes = encodedJson().getBytes(StandardCharsets.UTF_8).length;
		long estimate = 192L + bytes;
		return (int) Math.min(Integer.MAX_VALUE, estimate);
	}

	String encodedJson() {
		String encoded = encodedJson;
		if (encoded != null) return encoded;
		encodedJson = encoded = DiagnosticJson.encode(this);
		return encoded;
	}

	private static long estimate(Object value) {
		if (value == null) return 4L;
		if (value instanceof CharSequence) return 8L + ((CharSequence) value).length() * 3L;
		if (value instanceof Number || value instanceof Boolean) return 24L;
		if (value instanceof Map<?, ?>) {
			long size = 16L;
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				size += estimate(String.valueOf(entry.getKey())) + estimate(entry.getValue());
			}
			return size;
		}
		if (value instanceof Iterable<?>) {
			long size = 16L;
			for (Object item : (Iterable<?>) value) size += estimate(item);
			return size;
		}
		return estimate(String.valueOf(value));
	}

	DiagnosticEvent withEnvelope(long sequence, long wallTimeMillis, long elapsedRealtimeMillis,
			String sessionId, String processName, int processId, String threadName, long threadId,
			DiagnosticSanitizer sanitizer) {
		return new DiagnosticEvent(sequence, wallTimeMillis, elapsedRealtimeMillis,
				sanitizer.sanitize("session_id", sessionId),
				sanitizer.sanitizeCorrelationIdentifier(operationId),
				sanitizer.sanitize("process_name", processName), processId,
				sanitizer.sanitize("thread_name", threadName), threadId, scope, priority,
				sanitizer.sanitizeCategoryIdentifier(category),
				sanitizer.sanitizeEventIdentifier(name),
				sanitizer.sanitizeEventAttributes(category, name, attributes));
	}

	public static final class Builder {
		private final String category;
		private final String name;
		private final Map<String, Object> attributes = new LinkedHashMap<>();
		private String operationId;
		private DiagnosticScope scope = DiagnosticScope.ESSENTIAL;
		private DiagnosticPriority priority = DiagnosticPriority.STATE;

		private Builder(String category, String name) {
			if ((category == null) || category.trim().isEmpty()) {
				throw new IllegalArgumentException("category must not be empty");
			}
			if ((name == null) || name.trim().isEmpty()) {
				throw new IllegalArgumentException("name must not be empty");
			}
			this.category = category;
			this.name = name;
		}

		public Builder operationId(String value) {
			operationId = value;
			return this;
		}

		public Builder scope(DiagnosticScope value) {
			if (value == null) throw new NullPointerException("scope");
			scope = value;
			return this;
		}

		public Builder priority(DiagnosticPriority value) {
			if (value == null) throw new NullPointerException("priority");
			priority = value;
			return this;
		}

		public Builder put(String key, Object value) {
			if ((key == null) || key.trim().isEmpty()) {
				throw new IllegalArgumentException("attribute key must not be empty");
			}
			attributes.put(key, value);
			return this;
		}

		public Builder attributes(Map<String, ?> values) {
			if (values != null) {
				for (Map.Entry<String, ?> entry : values.entrySet()) {
					put(entry.getKey(), entry.getValue());
				}
			}
			return this;
		}

		public Builder error(Throwable error) {
			if (error == null) return this;
			attributes.put("error", error);
			priority = DiagnosticPriority.ERROR;
			return this;
		}

		public DiagnosticEvent build() {
			return new DiagnosticEvent(this);
		}
	}
}
