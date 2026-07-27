package me.aap.fermata.addon.stremio.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.aap.fermata.addon.stremio.protocol.model.CatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.CatalogExtra;
import me.aap.fermata.addon.stremio.protocol.model.AddonCatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.ManifestBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.model.PrefixConstraint;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ManifestValidator {
	public static final int MAX_MANIFEST_BYTES = 512 * 1024;
	public static final int MAX_STRING_CHARS = 4_096;
	public static final int MAX_RESOURCES = 64;
	public static final int MAX_CATALOGS = 128;
	public static final int MAX_ADDON_CATALOGS = 32;
	public static final int MAX_LIST_VALUES = 256;
	public static final int MAX_EXTRAS = 32;
	public static final int MAX_JSON_DEPTH = 16;
	public static final int MAX_JSON_NODES = 4_096;

	private ManifestValidator() {
	}

	public static StremioManifest parse(String json) {
		Objects.requireNonNull(json, "json");
		if ((json.length() > MAX_MANIFEST_BYTES) ||
				(json.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES)) {
			throw error("$", "Manifest exceeds " + MAX_MANIFEST_BYTES + " bytes");
		}
		var trimmed = json.trim();
		if (trimmed.isEmpty()) throw error("$", "Manifest is empty");
		if (trimmed.startsWith("<")) {
			throw error("$", "Expected a JSON manifest but received HTML-like content");
		}

		final JSONObject root;
		try {
			root = new JSONObject(trimmed);
		} catch (JSONException e) {
			throw new ManifestValidationException("$", "Malformed manifest JSON", e);
		}
		validateShape(root, 0, new int[]{0}, "$");

		var id = requiredText(root, "id", "$.id");
		var name = requiredText(root, "name", "$.name");
		var description = optionalText(root, "description", "$.description");
		if (description == null) description = "";
		var version = requiredText(root, "version", "$.version");
		var types = requiredStringList(root, "types", "$.types", false);
		var prefixes = prefixConstraint(root, "idPrefixes", "$.idPrefixes");
		var resources = parseResources(requiredArray(root, "resources", "$.resources"));
		var catalogs = parseCatalogs(requiredArray(root, "catalogs", "$.catalogs"));
		var addonCatalogs = parseAddonCatalogs(root);
		var hints = parseBehaviorHints(root);

		try {
			return new StremioManifest(id, name, description, version, types, prefixes,
					resources, catalogs, addonCatalogs, hints);
		} catch (IllegalArgumentException e) {
			throw new ManifestValidationException("$", "Invalid manifest: " + e.getMessage(), e);
		}
	}

	private static List<AddonCatalogCapability> parseAddonCatalogs(JSONObject root) {
		if (!root.has("addonCatalogs") || root.isNull("addonCatalogs")) return List.of();
		JSONArray array = array(root, "addonCatalogs", "$.addonCatalogs");
		requireLimit(array, MAX_ADDON_CATALOGS, "$.addonCatalogs");
		var catalogs = new ArrayList<AddonCatalogCapability>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			String field = "$.addonCatalogs[" + i + ']';
			JSONObject catalog = objectAt(array, i, field);
			String type = requiredText(catalog, "type", field + ".type");
			String id = requiredText(catalog, "id", field + ".id");
			if (!identities.add(type + '\u001f' + id)) {
				throw error(field, "Duplicate addon catalog identity: " + type + '/' + id);
			}
			catalogs.add(new AddonCatalogCapability(type, id,
					optionalText(catalog, "name", field + ".name")));
		}
		return List.copyOf(catalogs);
	}

	private static List<ResourceCapability> parseResources(JSONArray array) {
		if (array.length() == 0) throw error("$.resources", "At least one resource is required");
		requireLimit(array, MAX_RESOURCES, "$.resources");
		var resources = new ArrayList<ResourceCapability>(array.length());
		for (int i = 0; i < array.length(); i++) {
			var field = "$.resources[" + i + "]";
			var value = array.opt(i);
			if (value instanceof String name) {
				resources.add(ResourceCapability.inherited(requireText(name, field)));
			} else if (value instanceof JSONObject resource) {
				var name = requiredText(resource, "name", field + ".name");
				var types = requiredStringList(resource, "types", field + ".types", false);
				var prefixes = prefixConstraint(resource, "idPrefixes", field + ".idPrefixes");
				resources.add(ResourceCapability.constrained(name, types, prefixes));
			} else {
				throw error(field, "Resource must be a string or object");
			}
		}
		return List.copyOf(resources);
	}

	private static List<CatalogCapability> parseCatalogs(JSONArray array) {
		requireLimit(array, MAX_CATALOGS, "$.catalogs");
		var catalogs = new ArrayList<CatalogCapability>(array.length());
		var identities = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			var field = "$.catalogs[" + i + "]";
			var catalog = objectAt(array, i, field);
			var type = requiredText(catalog, "type", field + ".type");
			var id = requiredText(catalog, "id", field + ".id");
			var identity = type + '\u001f' + id;
			if (!identities.add(identity)) {
				throw error(field, "Duplicate catalog identity: " + type + "/" + id);
			}
			var name = optionalText(catalog, "name", field + ".name");
			catalogs.add(new CatalogCapability(type, id, name, parseCatalogExtras(catalog, field)));
		}
		return List.copyOf(catalogs);
	}

	private static List<CatalogExtra> parseCatalogExtras(JSONObject catalog, String field) {
		var extras = new LinkedHashMap<String, CatalogExtra>();
		if (catalog.has("extra") && !catalog.isNull("extra")) {
			var array = array(catalog, "extra", field + ".extra");
			requireLimit(array, MAX_EXTRAS, field + ".extra");
			for (int i = 0; i < array.length(); i++) {
				var extraField = field + ".extra[" + i + "]";
				var value = objectAt(array, i, extraField);
				var name = requiredText(value, "name", extraField + ".name");
				if (extras.containsKey(name)) throw error(extraField, "Duplicate extra: " + name);
				var required = optionalBoolean(value, "isRequired", false, extraField + ".isRequired");
				var options = optionalStringList(value, "options", extraField + ".options");
				var limit = optionalPositiveInt(value, "optionsLimit", 1,
						extraField + ".optionsLimit");
				extras.put(name, new CatalogExtra(name, required, options, limit));
			}
		}

		// Older v3 manifests use these fields. Normalize them into the same immutable model.
		var supported = optionalStringList(catalog, "extraSupported", field + ".extraSupported");
		var required = Set.copyOf(optionalStringList(catalog, "extraRequired",
				field + ".extraRequired"));
		for (var name : supported) {
			var old = extras.get(name);
			if (old == null) extras.put(name, new CatalogExtra(name, required.contains(name), List.of(), 1));
			else if (required.contains(name) && !old.required()) {
				extras.put(name, new CatalogExtra(name, true, old.options(), old.optionsLimit()));
			}
		}
		for (var name : required) {
			extras.computeIfAbsent(name, key -> new CatalogExtra(key, true, List.of(), 1));
		}

		var genres = optionalStringList(catalog, "genres", field + ".genres");
		if (!genres.isEmpty()) {
			var genre = extras.get("genre");
			if (genre == null) extras.put("genre", new CatalogExtra("genre", false, genres, 1));
			else if (genre.options().isEmpty()) {
				extras.put("genre", new CatalogExtra("genre", genre.required(), genres,
						genre.optionsLimit()));
			}
		}
		return List.copyOf(extras.values());
	}

	private static ManifestBehaviorHints parseBehaviorHints(JSONObject root) {
		if (!root.has("behaviorHints") || root.isNull("behaviorHints")) {
			return ManifestBehaviorHints.NONE;
		}
		var hints = object(root, "behaviorHints", "$.behaviorHints");
		return new ManifestBehaviorHints(
				optionalBoolean(hints, "configurable", false, "$.behaviorHints.configurable"),
				optionalBoolean(hints, "configurationRequired", false,
						"$.behaviorHints.configurationRequired"),
				optionalBoolean(hints, "adult", false, "$.behaviorHints.adult"),
				optionalBoolean(hints, "p2p", false, "$.behaviorHints.p2p"));
	}

	private static PrefixConstraint prefixConstraint(JSONObject object, String key, String field) {
		if (!object.has(key) || object.isNull(key)) return PrefixConstraint.unrestricted();
		return PrefixConstraint.restricted(stringList(array(object, key, field), field, true));
	}

	private static List<String> requiredStringList(
			JSONObject object, String key, String field, boolean allowEmpty) {
		return stringList(requiredArray(object, key, field), field, allowEmpty);
	}

	private static List<String> optionalStringList(JSONObject object, String key, String field) {
		if (!object.has(key) || object.isNull(key)) return List.of();
		return stringList(array(object, key, field), field, true);
	}

	private static List<String> stringList(JSONArray array, String field, boolean allowEmpty) {
		if (!allowEmpty && array.length() == 0) throw error(field, "Array cannot be empty");
		requireLimit(array, MAX_LIST_VALUES, field);
		var result = new ArrayList<String>(array.length());
		var unique = new LinkedHashSet<String>();
		for (int i = 0; i < array.length(); i++) {
			var value = array.opt(i);
			if (!(value instanceof String text)) {
				throw error(field + "[" + i + "]", "Expected a string");
			}
			text = requireText(text, field + "[" + i + "]");
			if (!unique.add(text)) throw error(field + "[" + i + "]", "Duplicate value: " + text);
			result.add(text);
		}
		return List.copyOf(result);
	}

	private static String requiredText(JSONObject object, String key, String field) {
		if (!object.has(key) || object.isNull(key)) throw error(field, "Required field is missing");
		var value = object.opt(key);
		if (!(value instanceof String text)) throw error(field, "Expected a string");
		return requireText(text, field);
	}

	private static String optionalText(JSONObject object, String key, String field) {
		if (!object.has(key) || object.isNull(key)) return null;
		var value = object.opt(key);
		if (!(value instanceof String text)) throw error(field, "Expected a string");
		return requireText(text, field);
	}

	private static String requireText(String value, String field) {
		if (value.isBlank()) throw error(field, "String cannot be blank");
		if (value.length() > MAX_STRING_CHARS) {
			throw error(field, "String exceeds " + MAX_STRING_CHARS + " characters");
		}
		return value;
	}

	private static boolean optionalBoolean(
			JSONObject object, String key, boolean defaultValue, String field) {
		if (!object.has(key) || object.isNull(key)) return defaultValue;
		var value = object.opt(key);
		if (!(value instanceof Boolean bool)) throw error(field, "Expected a boolean");
		return bool;
	}

	private static int optionalPositiveInt(
			JSONObject object, String key, int defaultValue, String field) {
		if (!object.has(key) || object.isNull(key)) return defaultValue;
		var value = object.opt(key);
		if (!(value instanceof Number number)) throw error(field, "Expected a number");
		var longValue = number.longValue();
		if ((number.doubleValue() != longValue) || (longValue < 1) ||
				(longValue > MAX_LIST_VALUES)) {
			throw error(field, "Expected a positive integer");
		}
		return (int) longValue;
	}

	private static JSONArray requiredArray(JSONObject object, String key, String field) {
		if (!object.has(key) || object.isNull(key)) throw error(field, "Required field is missing");
		return array(object, key, field);
	}

	private static JSONArray array(JSONObject object, String key, String field) {
		var value = object.opt(key);
		if (!(value instanceof JSONArray array)) throw error(field, "Expected an array");
		return array;
	}

	private static JSONObject object(JSONObject owner, String key, String field) {
		var value = owner.opt(key);
		if (!(value instanceof JSONObject object)) throw error(field, "Expected an object");
		return object;
	}

	private static JSONObject objectAt(JSONArray array, int index, String field) {
		var value = array.opt(index);
		if (!(value instanceof JSONObject object)) throw error(field, "Expected an object");
		return object;
	}

	private static void requireLimit(JSONArray array, int limit, String field) {
		if (array.length() > limit) throw error(field, "Array exceeds " + limit + " items");
	}

	private static void validateShape(Object value, int depth, int[] nodes, String field) {
		if (depth > MAX_JSON_DEPTH) throw error(field, "Manifest nesting is too deep");
		if (++nodes[0] > MAX_JSON_NODES) throw error(field, "Manifest has too many values");
		if (value instanceof JSONObject object) {
			var keys = object.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				validateShape(object.opt(key), depth + 1, nodes, field + '.' + key);
			}
		} else if (value instanceof JSONArray array) {
			for (int i = 0; i < array.length(); i++) {
				validateShape(array.opt(i), depth + 1, nodes, field + '[' + i + ']');
			}
		}
	}

	private static ManifestValidationException error(String field, String message) {
		return new ManifestValidationException(field, message);
	}
}
