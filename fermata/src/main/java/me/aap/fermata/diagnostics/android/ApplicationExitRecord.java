package me.aap.fermata.diagnostics.android;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import me.aap.fermata.diagnostics.DiagnosticPriority;
import me.aap.fermata.diagnostics.DiagnosticSanitizer;

final class ApplicationExitRecord {
	private static final int DESCRIPTION_LIMIT = 1024;
	private static final DiagnosticSanitizer SANITIZER =
			new DiagnosticSanitizer(DESCRIPTION_LIMIT);

	private final int reasonCode;
	private final String reason;
	private final int status;
	private final int importance;
	private final String processName;
	private final long pssKb;
	private final long rssKb;
	private final long timestamp;

	ApplicationExitRecord(int reasonCode, int status, int importance, String processName,
			long pssKb, long rssKb, long timestamp) {
		this.reasonCode = reasonCode;
		reason = reasonName(reasonCode);
		this.status = status;
		this.importance = importance;
		this.processName = SANITIZER.sanitize("process_name", processName);
		this.pssKb = Math.max(0L, pssKb);
		this.rssKb = Math.max(0L, rssKb);
		this.timestamp = Math.max(0L, timestamp);
	}

	long getTimestamp() {
		return timestamp;
	}

	DiagnosticPriority getPriority() {
		switch (reasonCode) {
			case 3: // REASON_LOW_MEMORY
			case 4: // REASON_CRASH
			case 5: // REASON_CRASH_NATIVE
			case 6: // REASON_ANR
			case 7: // REASON_INITIALIZATION_FAILURE
			case 9: // REASON_EXCESSIVE_RESOURCE_USAGE
				return DiagnosticPriority.ERROR;
			default:
				return DiagnosticPriority.STATE;
		}
	}

	Map<String, Object> toAttributes() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("reason", reason);
		attributes.put("reason_code", reasonCode);
		attributes.put("status", status);
		attributes.put("importance", importance);
		attributes.put("process_name", processName);
		attributes.put("pss_kb", pssKb);
		attributes.put("rss_kb", rssKb);
		attributes.put("timestamp", timestamp);
		return Collections.unmodifiableMap(attributes);
	}

	static String reasonName(int reason) {
		switch (reason) {
			case 0:
				return "unknown";
			case 1:
				return "exit_self";
			case 2:
				return "signaled";
			case 3:
				return "low_memory";
			case 4:
				return "crash";
			case 5:
				return "native_crash";
			case 6:
				return "anr";
			case 7:
				return "initialization_failure";
			case 8:
				return "permission_change";
			case 9:
				return "excessive_resource_usage";
			case 10:
				return "user_requested";
			case 11:
				return "user_stopped";
			case 12:
				return "dependency_died";
			case 13:
				return "other";
			case 14:
				return "freezer";
			case 15:
				return "package_state_change";
			case 16:
				return "package_updated";
			default:
				return "reason_" + reason;
		}
	}
}
