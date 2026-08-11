package me.aap.fermata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;

import android.view.KeyEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Short, explicit capture session used by the Settings vehicle-control diagnostic. */
public final class HardwareInputTestSession {
	private static final long DURATION_MILLIS = 30_000L;
	private static final Map<Integer, Entry> entries = new LinkedHashMap<>();
	private static long startedAt;
	private static boolean active;

	private HardwareInputTestSession() {
	}

	public static synchronized Result toggle() {
		long now = uptimeMillis();
		if (!active) {
			active = true;
			startedAt = now;
			entries.clear();
			return Result.started();
		}
		String summary = summary(now);
		active = false;
		startedAt = 0L;
		entries.clear();
		return Result.completed(summary);
	}

	static synchronized void observe(HardwareInputEvent event, String disposition) {
		if (!active || ((uptimeMillis() - startedAt) > DURATION_MILLIS) ||
				!event.isDiagnosticControl()) return;
		Entry entry = entries.computeIfAbsent(event.keyCode(), ignored -> new Entry());
		if ((event.action() == ACTION_DOWN) && (event.repeatCount() == 0) &&
				(entry.lastDownTime != event.downTime())) {
			entry.presses++;
			entry.lastDownTime = event.downTime();
		}
		if (event.origin() == HardwareInputEvent.Origin.ACTIVITY) entry.activity = true;
		else entry.mediaSession = true;
		entry.dispositions.add(disposition);
	}

	private static String summary(long now) {
		if (entries.isEmpty()) return "";
		StringBuilder text = new StringBuilder();
		for (Map.Entry<Integer, Entry> result : entries.entrySet()) {
			if (text.length() > 0) text.append('\n');
			Entry entry = result.getValue();
			text.append(KeyEvent.keyCodeToString(result.getKey())).append(" (")
					.append(result.getKey()).append("): ");
			if (entry.activity) text.append("Activity");
			if (entry.activity && entry.mediaSession) text.append(" + ");
			if (entry.mediaSession) text.append("MediaSession");
			text.append(", ").append(String.join("/", entry.dispositions)).append(", presses=")
					.append(entry.presses);
		}
		if ((now - startedAt) > DURATION_MILLIS) text.append("\n(capture expired)");
		return text.toString();
	}

	static synchronized void resetForTest() {
		active = false;
		startedAt = 0L;
		entries.clear();
	}

	private static final class Entry {
		private int presses;
		private long lastDownTime = Long.MIN_VALUE;
		private boolean activity;
		private boolean mediaSession;
		private final Set<String> dispositions = new LinkedHashSet<>();
	}

	public static final class Result {
		private final boolean started;
		private final String summary;

		private Result(boolean started, String summary) {
			this.started = started;
			this.summary = summary;
		}

		static Result started() { return new Result(true, ""); }
		static Result completed(String summary) { return new Result(false, summary); }
		public boolean isStarted() { return started; }
		public String getSummary() { return summary; }
	}
}
