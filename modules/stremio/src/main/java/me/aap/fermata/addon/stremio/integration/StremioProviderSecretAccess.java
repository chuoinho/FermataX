package me.aap.fermata.addon.stremio.integration;

import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

/** Narrow encrypted-secret bridge required by protocol integration. */
@FunctionalInterface
public interface StremioProviderSecretAccess {
	CompletionStage<StremioSourceSecret> load(StremioSourceRecord source);
}
