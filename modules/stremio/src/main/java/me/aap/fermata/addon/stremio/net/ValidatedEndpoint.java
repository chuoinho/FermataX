package me.aap.fermata.addon.stremio.net;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

public record ValidatedEndpoint(
		NormalizedEndpoint endpoint,
		InetAddress pinnedAddress,
		AddressKind pinnedAddressKind,
		List<InetAddress> validatedAddresses) {
	public ValidatedEndpoint {
		Objects.requireNonNull(endpoint, "endpoint");
		Objects.requireNonNull(pinnedAddress, "pinnedAddress");
		Objects.requireNonNull(pinnedAddressKind, "pinnedAddressKind");
		validatedAddresses = List.copyOf(validatedAddresses);
		if (validatedAddresses.isEmpty()) throw new IllegalArgumentException("validatedAddresses is empty");
	}
}
