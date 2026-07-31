package me.aap.fermata.diagnostics.android;

final class BoundedThreadDump {
	private BoundedThreadDump() {
	}

	static String format(Thread thread, int maxChars) {
		if ((thread == null) || (maxChars <= 0)) return "";
		StringBuilder out = new StringBuilder(Math.min(maxChars, 4096));
		append(out, "thread=", maxChars);
		append(out, thread.getName(), maxChars);
		append(out, " state=", maxChars);
		append(out, thread.getState().name(), maxChars);
		for (StackTraceElement frame : thread.getStackTrace()) {
			if (!append(out, "\n\tat ", maxChars) ||
					!append(out, frame.toString(), maxChars)) break;
		}
		return out.toString();
	}

	private static boolean append(StringBuilder out, String value, int maxChars) {
		if (out.length() >= maxChars) return false;
		int remaining = maxChars - out.length();
		if (value.length() <= remaining) {
			out.append(value);
			return true;
		}
		out.append(value, 0, remaining);
		return false;
	}
}
