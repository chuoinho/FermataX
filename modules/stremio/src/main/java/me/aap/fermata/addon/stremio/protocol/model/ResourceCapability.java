package me.aap.fermata.addon.stremio.protocol.model;

import java.util.List;
import java.util.Objects;

public record ResourceCapability(
		String name,
		boolean inheritsManifestConstraints,
		List<String> types,
		PrefixConstraint idPrefixes) {

	public ResourceCapability {
		name = requireText(name, "name");
		types = List.copyOf(Objects.requireNonNull(types, "types"));
		idPrefixes = Objects.requireNonNull(idPrefixes, "idPrefixes");
		if (inheritsManifestConstraints && (!types.isEmpty() || idPrefixes.declared())) {
			throw new IllegalArgumentException("Inherited resource cannot define local constraints");
		}
		if (!inheritsManifestConstraints && types.isEmpty()) {
			throw new IllegalArgumentException("Resource-level types cannot be empty");
		}
	}

	public static ResourceCapability inherited(String name) {
		return new ResourceCapability(name, true, List.of(), PrefixConstraint.unrestricted());
	}

	public static ResourceCapability constrained(
			String name, List<String> types, PrefixConstraint idPrefixes) {
		return new ResourceCapability(name, false, types, idPrefixes);
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
