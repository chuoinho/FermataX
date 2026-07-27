package me.aap.fermata.addon.stremio.protocol;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.model.CatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.CatalogExtra;
import me.aap.fermata.addon.stremio.protocol.model.ProviderCapability;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

public final class CapabilityMatcher {
	private CapabilityMatcher() {
	}

	public static boolean supports(ProviderCapability provider, StremioRequest request) {
		Objects.requireNonNull(provider, "provider");
		return provider.enabled() && supports(provider.manifest(), request);
	}

	public static boolean supports(StremioManifest manifest, StremioRequest request) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(manifest, "manifest");
		if (request.resource().equals("catalog")) {
			return manifest.catalogs().stream()
					.filter(catalog -> matchesCatalog(catalog, request.type(), request.id()))
					.anyMatch(catalog -> matchesExtras(catalog, request.extras()));
		}
		if (request.resource().equals("addon_catalog")) {
			return manifest.addonCatalogs().stream().anyMatch(catalog ->
					catalog.type().equals(request.type()) && catalog.id().equals(request.id()) &&
					request.extras().isEmpty());
		}
		return supportsResource(manifest, request.resource(), request.type(), request.id());
	}

	public static boolean supports(
			StremioManifest manifest, String resource, String type, String id) {
		Objects.requireNonNull(manifest, "manifest");
		Objects.requireNonNull(resource, "resource");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(id, "id");

		if (resource.equals("catalog")) return manifest.catalogs().stream()
				.anyMatch(catalog -> matchesCatalog(catalog, type, id) &&
						matchesExtras(catalog, Map.of()));
		if (resource.equals("addon_catalog")) return manifest.addonCatalogs().stream()
				.anyMatch(catalog -> catalog.type().equals(type) && catalog.id().equals(id));
		return supportsResource(manifest, resource, type, id);
	}

	private static boolean supportsResource(
			StremioManifest manifest, String resource, String type, String id) {
		return manifest.resources().stream()
				.filter(capability -> capability.name().equals(resource))
				.anyMatch(capability -> matchesConstraints(manifest, capability, type, id));
	}

	private static boolean matchesCatalog(CatalogCapability catalog, String type, String id) {
		return catalog.type().equals(type) && catalog.id().equals(id);
	}

	private static boolean matchesExtras(CatalogCapability catalog, Map<String, ?> requested) {
		for (var name : requested.keySet()) {
			if ((name == null) || catalog.extra(name).isEmpty()) return false;
		}
		for (var capability : catalog.extras()) {
			var values = extraValues(requested.get(capability.name()));
			if (capability.required() && values.isEmpty()) return false;
			if (!matchesExtra(capability, values)) return false;
		}
		return true;
	}

	private static boolean matchesExtra(CatalogExtra capability, List<String> values) {
		if (values.size() > capability.optionsLimit()) return false;
		return capability.options().isEmpty() || capability.options().containsAll(values);
	}

	private static List<String> extraValues(Object value) {
		if (value == null) return List.of();
		var values = new ArrayList<String>();
		if (value instanceof Iterable<?> iterable) {
			for (var item : iterable) addExtraValue(values, item);
		} else if (value.getClass().isArray()) {
			for (int i = 0; i < Array.getLength(value); i++) {
				addExtraValue(values, Array.get(value, i));
			}
		} else {
			addExtraValue(values, value);
		}
		return values;
	}

	private static void addExtraValue(List<String> values, Object value) {
		if (value == null) return;
		var text = String.valueOf(value);
		if (!text.isEmpty()) values.add(text);
	}

	private static boolean matchesConstraints(StremioManifest manifest,
			ResourceCapability capability, String type, String id) {
		var types = capability.inheritsManifestConstraints() ? manifest.types() : capability.types();
		var prefixes = capability.inheritsManifestConstraints()
				? manifest.idPrefixes() : capability.idPrefixes();
		return types.contains(type) && prefixes.matches(id);
	}
}
