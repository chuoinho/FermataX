package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

/** Usenet target. Credentials remain in memory and are never rendered or persisted. */
public record NzbStreamTarget(
		String url,
		List<String> servers,
		Integer fileIndex,
		String fileMustInclude) implements StreamTarget {
	public NzbStreamTarget {
		if (Objects.requireNonNull(url, "url").isBlank()) {
			throw new IllegalArgumentException("url cannot be blank");
		}
		servers = List.copyOf(Objects.requireNonNull(servers, "servers"));
		if ((fileIndex != null) && (fileIndex < 0)) {
			throw new IllegalArgumentException("fileIndex cannot be negative");
		}
	}

	@Override
	public String toString() {
		return "NzbStreamTarget[url=<redacted>, servers=" + servers.size() +
				", fileIndex=" + fileIndex + ", fileMustInclude=<redacted>]";
	}
}
