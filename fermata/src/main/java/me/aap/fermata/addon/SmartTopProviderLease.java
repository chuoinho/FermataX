package me.aap.fermata.addon;

/** Exact loaded-addon lease. Only AddonManager can construct a valid instance. */
public final class SmartTopProviderLease {
	private final FermataAddon addon;
	private final String addonClass;
	private final long lifecycleGeneration;

	SmartTopProviderLease(FermataAddon addon, long lifecycleGeneration) {
		this.addon = addon;
		addonClass = addon.getClass().getName();
		this.lifecycleGeneration = lifecycleGeneration;
	}

	FermataAddon addon() {
		return addon;
	}

	public String addonClass() {
		return addonClass;
	}

	public long lifecycleGeneration() {
		return lifecycleGeneration;
	}
}
