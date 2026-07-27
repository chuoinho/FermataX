package me.aap.fermata.addon.stremio.net.cache;

public record CacheLookup(CacheState state, CacheEntry entry) {
	public static CacheLookup miss() {
		return new CacheLookup(CacheState.MISS, null);
	}
}
