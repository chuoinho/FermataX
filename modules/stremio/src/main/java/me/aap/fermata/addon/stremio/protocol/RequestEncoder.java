package me.aap.fermata.addon.stremio.protocol;

import java.lang.reflect.Array;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;

public final class RequestEncoder {
	private RequestEncoder() {
	}

	public static String encodePath(StremioRequest request) {
		Objects.requireNonNull(request, "request");
		return encodePath(request.resource(), request.type(), request.id(), request.extras());
	}

	public static String encodePath(
			String resource, String type, String id, Map<String, ?> extras) {
		var path = new StringBuilder(96)
				.append('/')
				.append(encodeComponent(requireText(resource, "resource"))).append('/')
				.append(encodeComponent(requireText(type, "type"))).append('/')
				.append(encodeComponent(requireText(id, "id")));
		var encodedExtras = encodeExtras(Objects.requireNonNull(extras, "extras"));
		if (!encodedExtras.isEmpty()) path.append('/').append(encodedExtras);
		return path.append(".json").toString();
	}

	public static URI resolve(URI manifestUri, StremioRequest request) {
		Objects.requireNonNull(manifestUri, "manifestUri");
		if (!manifestUri.isAbsolute() || (manifestUri.getRawAuthority() == null)) {
			throw new IllegalArgumentException("Manifest URI must be absolute");
		}
		var scheme = manifestUri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException("Manifest URI must use HTTP or HTTPS");
		}
		var manifestPath = manifestUri.getRawPath();
		var suffix = "/manifest.json";
		if ((manifestPath == null) || !manifestPath.endsWith(suffix)) {
			throw new IllegalArgumentException("Manifest URI path must end with /manifest.json");
		}
		var basePath = manifestPath.substring(0, manifestPath.length() - suffix.length());
		var pathRequest = request;
		var encodedPath = encodePath(pathRequest.resource(), pathRequest.type(),
				pathRequest.id(), pathRequest.resource().equals("subtitles") ? Map.of() :
					pathRequest.extras());
		var url = new StringBuilder()
				.append(scheme).append("://").append(manifestUri.getRawAuthority())
				.append(basePath).append(encodedPath);
		var query = new StringBuilder();
		if (request.resource().equals("subtitles")) {
			query.append(encodeQuery(request.extras()));
		}
		if (manifestUri.getRawQuery() != null) {
			if (query.length() > 0) query.append('&');
			query.append(manifestUri.getRawQuery());
		}
		if (query.length() > 0) url.append('?').append(query);
		return URI.create(url.toString());
	}

	private static String encodeQuery(Map<String, ?> extras) {
		var pairs = new ArrayList<String>();
		for (var key : extras.keySet().stream().sorted().toList()) {
			requireText(key, "extra key");
			for (var value : values(extras.get(key))) {
				if ((value == null) || value.isEmpty()) continue;
				pairs.add(encodeComponent(key) + '=' + encodeComponent(value));
			}
		}
		return String.join("&", pairs);
	}

	private static String encodeExtras(Map<String, ?> extras) {
		var keys = extras.keySet().stream()
				.peek(key -> requireText(key, "extra key"))
				.sorted(Comparator.naturalOrder())
				.toList();
		var pairs = new ArrayList<String>();
		for (var key : keys) {
			for (var value : values(extras.get(key))) {
				if ((value == null) || value.isEmpty()) continue;
				pairs.add(encodeComponent(key) + '=' + encodeComponent(value));
			}
		}
		return String.join("&", pairs);
	}

	private static List<String> values(Object value) {
		if (value == null) return List.of();
		if (value instanceof Iterable<?> iterable) {
			var values = new ArrayList<String>();
			for (var item : iterable) values.add(item == null ? null : String.valueOf(item));
			return values;
		}
		if (value.getClass().isArray()) {
			var values = new ArrayList<String>(Array.getLength(value));
			for (int i = 0; i < Array.getLength(value); i++) {
				var item = Array.get(value, i);
				values.add(item == null ? null : String.valueOf(item));
			}
			return values;
		}
		return List.of(String.valueOf(value));
	}

	private static String encodeComponent(String value) {
		var bytes = value.getBytes(StandardCharsets.UTF_8);
		var encoded = new StringBuilder(bytes.length);
		for (byte raw : bytes) {
			var valueByte = raw & 0xff;
			if (isUnreserved(valueByte)) {
				encoded.append((char) valueByte);
			} else {
				encoded.append('%');
				encoded.append(Character.toUpperCase(Character.forDigit(valueByte >>> 4, 16)));
				encoded.append(Character.toUpperCase(Character.forDigit(valueByte & 0x0f, 16)));
			}
		}
		return encoded.toString();
	}

	private static boolean isUnreserved(int value) {
		return ((value >= 'a') && (value <= 'z')) || ((value >= 'A') && (value <= 'Z')) ||
				((value >= '0') && (value <= '9')) || (value == '-') || (value == '.') ||
				(value == '_') || (value == '~');
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
