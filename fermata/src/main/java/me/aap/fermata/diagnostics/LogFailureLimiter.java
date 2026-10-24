package me.aap.fermata.diagnostics;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed-window rate limit for logged warnings and errors, per call site and in total, so a warning
 * logged in a loop cannot push other failures out of the journal. Counts what it drops.
 */
public final class LogFailureLimiter {
	private final int perSite;
	private final int total;
	private final long windowMs;
	private final Map<String, Integer> counts = new HashMap<>();
	private final Map<String, Integer> dropped = new LinkedHashMap<>();
	private long windowStart = Long.MIN_VALUE;
	private int windowTotal;

	public LogFailureLimiter(int perSite, int total, long windowMs) {
		this.perSite = perSite;
		this.total = total;
		this.windowMs = windowMs;
	}

	public synchronized boolean tryAcquire(String callSite, long nowMs) {
		if ((windowStart == Long.MIN_VALUE) || (nowMs - windowStart >= windowMs) || (nowMs < windowStart)) {
			windowStart = nowMs;
			windowTotal = 0;
			counts.clear();
		}
		int site = counts.getOrDefault(callSite, 0);
		if ((site >= perSite) || (windowTotal >= total)) {
			dropped.merge(callSite, 1, Integer::sum);
			return false;
		}
		counts.put(callSite, site + 1);
		windowTotal++;
		return true;
	}

	/**
	 * Records a logged warning or error as a detailed event identified by its call site. The log
	 * message is never recorded: free text may carry titles, accounts or coordinates.
	 */
	public boolean record(DiagnosticRecorder recorder, boolean error, Throwable failure,
			StackTraceElement caller, long nowMs) {
		String site = callSite(caller);
		if (!tryAcquire(site, nowMs)) return false;
		// Only an accepted record reports the drops, so a loop yields one count per window.
		for (Map.Entry<String, Integer> e : drainDropped().entrySet()) {
			recorder.recordDetailed("log", "log_dropped", null,
					Map.of("call_site", e.getKey(), "count", e.getValue()));
		}
		DiagnosticEvent.Builder event = DiagnosticEvent.builder("log", error ? "log_error" : "log_warning")
				.scope(DiagnosticScope.DETAILED)
				.priority(error ? DiagnosticPriority.ERROR : DiagnosticPriority.WARN)
				.put("call_site", site);
		if (failure != null) event.error(failure);
		return recorder.record(event.build());
	}

	static String callSite(StackTraceElement caller) {
		if (caller == null) return "unknown";
		String cls = caller.getClassName();
		return cls.substring(cls.lastIndexOf('.') + 1) + '.' + caller.getMethodName() + ':'
				+ caller.getLineNumber();
	}

	public synchronized Map<String, Integer> drainDropped() {
		if (dropped.isEmpty()) return Collections.emptyMap();
		Map<String, Integer> result = new LinkedHashMap<>(dropped);
		dropped.clear();
		return result;
	}
}
