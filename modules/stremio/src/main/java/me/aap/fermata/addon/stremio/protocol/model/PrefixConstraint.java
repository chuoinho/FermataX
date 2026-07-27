package me.aap.fermata.addon.stremio.protocol.model;

import java.util.List;
import java.util.Objects;

public record PrefixConstraint(boolean declared, List<String> prefixes) {
	private static final PrefixConstraint UNRESTRICTED = new PrefixConstraint(false, List.of());

	public PrefixConstraint {
		prefixes = List.copyOf(Objects.requireNonNull(prefixes, "prefixes"));
		if (!declared && !prefixes.isEmpty()) {
			throw new IllegalArgumentException("Undeclared prefix constraint cannot contain prefixes");
		}
	}

	public static PrefixConstraint unrestricted() {
		return UNRESTRICTED;
	}

	public static PrefixConstraint restricted(List<String> prefixes) {
		return new PrefixConstraint(true, prefixes);
	}

	public boolean matches(String id) {
		Objects.requireNonNull(id, "id");
		return !declared || prefixes.stream().anyMatch(id::startsWith);
	}
}
