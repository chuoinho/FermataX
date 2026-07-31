package me.aap.fermata.diagnostics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Correlates a started operation with progress and one terminal outcome. */
public final class DiagnosticOperation implements AutoCloseable {
	private final DiagnosticRecorder recorder;
	private final String category;
	private final String operationName;
	private final String operationId;
	private final AtomicBoolean terminal = new AtomicBoolean();

	DiagnosticOperation(DiagnosticRecorder recorder, String category, String operationName,
			String operationId, Map<String, ?> attributes) {
		this.recorder = recorder;
		this.category = category;
		this.operationName = operationName;
		this.operationId = operationId;
		record("operation_started", DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
				attributes, null);
	}

	public String getId() {
		return operationId;
	}

	boolean hasTerminalOutcome() {
		return terminal.get();
	}

	public boolean state(String eventName, Map<String, ?> attributes) {
		if (terminal.get()) return false;
		return record(eventName, DiagnosticScope.ESSENTIAL, DiagnosticPriority.STATE,
				attributes, null);
	}

	public boolean detail(String eventName, Map<String, ?> attributes) {
		if (terminal.get()) return false;
		return record(eventName, DiagnosticScope.DETAILED, DiagnosticPriority.DETAIL,
				attributes, null);
	}

	public boolean warn(String eventName, Map<String, ?> attributes) {
		if (terminal.get()) return false;
		return record(eventName, DiagnosticScope.ESSENTIAL, DiagnosticPriority.WARN,
				attributes, null);
	}

	public boolean complete(Map<String, ?> attributes) {
		return terminal("operation_completed", DiagnosticPriority.STATE, attributes, null);
	}

	public boolean fail(Throwable error, Map<String, ?> attributes) {
		return terminal("operation_failed", DiagnosticPriority.ERROR, attributes, error);
	}

	public boolean cancel(Map<String, ?> attributes) {
		return terminal("operation_cancelled", DiagnosticPriority.STATE, attributes, null);
	}

	public boolean timeout(Map<String, ?> attributes) {
		return terminal("operation_timed_out", DiagnosticPriority.WARN, attributes, null);
	}

	@Override
	public void close() {
		cancel(Collections.emptyMap());
	}

	private boolean record(String eventName, DiagnosticScope scope, DiagnosticPriority priority,
			Map<String, ?> attributes, Throwable error) {
		DiagnosticEvent.Builder event = DiagnosticEvent.builder(category, eventName)
				.operationId(operationId)
				.scope(scope)
				.priority(priority)
				.put("operation", operationName)
				.attributes(attributes);
		if (error != null) event.error(error);
		return recorder.record(event.build());
	}

	private boolean terminal(String eventName, DiagnosticPriority priority,
			Map<String, ?> attributes, Throwable error) {
		if (!terminal.compareAndSet(false, true)) return false;
		boolean accepted = false;
		try {
			accepted = record(eventName, DiagnosticScope.ESSENTIAL, priority, attributes, error);
			return accepted;
		} finally {
			if (!accepted) terminal.compareAndSet(true, false);
		}
	}
}
