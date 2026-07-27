package me.aap.fermata.addon.external;

import java.net.URI;

/**
 * Short-lived policy attached by the source addon to a browser handoff.
 *
 * <p>The receiving addon must validate the initial page and every subsequent main-frame
 * navigation. Implementations may bind validation to source consent, revision and expiry.</p>
 */
@FunctionalInterface
public interface ExternalNavigationPolicy extends AutoCloseable {
	void validate(URI uri) throws ExternalNavigationPolicyException;

	@Override
	default void close() {
	}
}
