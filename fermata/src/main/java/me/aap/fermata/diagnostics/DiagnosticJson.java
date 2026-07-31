package me.aap.fermata.diagnostics;

import java.util.Iterator;
import java.util.Map;

final class DiagnosticJson {
	private DiagnosticJson() {
	}

	static String encode(DiagnosticEvent event) {
		StringBuilder json = new StringBuilder(512);
		json.append('{');
		field(json, "schema_version", DiagnosticEvent.SCHEMA_VERSION);
		field(json, "sequence", event.getSequence());
		field(json, "timestamp_ms", event.getWallTimeMillis());
		field(json, "elapsed_ms", event.getElapsedRealtimeMillis());
		field(json, "session_id", event.getSessionId());
		if (event.getOperationId() != null) field(json, "operation_id", event.getOperationId());
		field(json, "process", event.getProcessName());
		field(json, "pid", event.getProcessId());
		field(json, "thread", event.getThreadName());
		field(json, "thread_id", event.getThreadId());
		field(json, "scope", event.getScope().name().toLowerCase());
		field(json, "priority", event.getPriority().name().toLowerCase());
		field(json, "category", event.getCategory());
		field(json, "event", event.getName());
		name(json, "data");
		value(json, event.getAttributes());
		json.append('}');
		return json.toString();
	}

	private static void field(StringBuilder json, String name, Object value) {
		name(json, name);
		value(json, value);
	}

	private static void name(StringBuilder json, String name) {
		if (json.length() > 1) json.append(',');
		string(json, name);
		json.append(':');
	}

	private static void value(StringBuilder json, Object value) {
		if (value == null) {
			json.append("null");
		} else if (value instanceof CharSequence) {
			string(json, value.toString());
		} else if (value instanceof Boolean) {
			json.append(value);
		} else if (value instanceof Number) {
			Number number = (Number) value;
			if ((number instanceof Double) && !Double.isFinite(number.doubleValue())) {
				json.append("null");
			} else if ((number instanceof Float) && !Float.isFinite(number.floatValue())) {
				json.append("null");
			} else {
				json.append(number);
			}
		} else if (value instanceof Map<?, ?>) {
			json.append('{');
			Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) value).entrySet().iterator();
			boolean first = true;
			while (iterator.hasNext()) {
				Map.Entry<?, ?> entry = iterator.next();
				if (!first) json.append(',');
				first = false;
				string(json, String.valueOf(entry.getKey()));
				json.append(':');
				value(json, entry.getValue());
			}
			json.append('}');
		} else if (value instanceof Iterable<?>) {
			json.append('[');
			boolean first = true;
			for (Object item : (Iterable<?>) value) {
				if (!first) json.append(',');
				first = false;
				value(json, item);
			}
			json.append(']');
		} else {
			string(json, String.valueOf(value));
		}
	}

	private static void string(StringBuilder json, String value) {
		json.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					json.append("\\\"");
					break;
				case '\\':
					json.append("\\\\");
					break;
				case '\b':
					json.append("\\b");
					break;
				case '\f':
					json.append("\\f");
					break;
				case '\n':
					json.append("\\n");
					break;
				case '\r':
					json.append("\\r");
					break;
				case '\t':
					json.append("\\t");
					break;
				default:
					if (c < 0x20) {
						json.append("\\u00");
						String hex = Integer.toHexString(c);
						if (hex.length() == 1) json.append('0');
						json.append(hex);
					} else {
						json.append(c);
					}
			}
		}
		json.append('"');
	}
}
