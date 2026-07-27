package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record CatalogResponse(List<StremioMeta> metas) {
	public CatalogResponse {
		metas = List.copyOf(Objects.requireNonNull(metas, "metas"));
	}

	@Override
	public String toString() {
		return "CatalogResponse[metas=" + metas.size() + "]";
	}
}
