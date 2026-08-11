package me.aap.fermata.addon;

import java.util.List;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Cached-only SmartTop boundary implemented alongside a loaded FermataAddon. */
public interface SmartTopProvider {
	FutureSupplier<List<SmartTopCandidate>> loadSmartTopCandidates(SmartTopProviderLease lease);

	FutureSupplier<PlayableItem> resolveSmartTopCandidate(DefaultMediaLib lib,
			SmartTopProviderLease lease, String opaqueId);
}
