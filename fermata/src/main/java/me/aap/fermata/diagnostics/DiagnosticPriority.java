package me.aap.fermata.diagnostics;

/** Controls queue retention when diagnostic events arrive faster than they can be written. */
public enum DiagnosticPriority {
	DETAIL(0),
	STATE(1),
	WARN(2),
	ERROR(3);

	private final int weight;

	DiagnosticPriority(int weight) {
		this.weight = weight;
	}

	int weight() {
		return weight;
	}
}
