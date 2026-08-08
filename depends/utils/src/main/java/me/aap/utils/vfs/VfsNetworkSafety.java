package me.aap.utils.vfs;

import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * Validation and error normalization at remote VFS protocol boundaries.
 * Remote servers are not trusted to return complete, well-formed directory entries.
 */
public final class VfsNetworkSafety {
	private VfsNetworkSafety() {
	}

	public static <T> List<T> requireDirectoryListing(String protocol, List<T> entries)
			throws VfsException {
		if (entries == null) throw malformed(protocol, "directory listing is missing");
		return entries;
	}

	public static <T> T requireEntry(String protocol, T entry) throws VfsException {
		if (entry == null) throw malformed(protocol, "directory entry is missing");
		return entry;
	}

	public static <T> T requireField(String protocol, String field, T value) throws VfsException {
		if (value == null) throw malformed(protocol, field + " is missing");
		return value;
	}

	public static String requireEntryName(String protocol, String name) throws VfsException {
		if (name == null || name.isEmpty()) throw malformed(protocol, "directory entry name is missing");
		if (name.equals(".") || name.equals("..")) return name;

		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '/' || c == '\\' || Character.isISOControl(c)) {
				throw malformed(protocol, "directory entry name is not a single path segment");
			}
		}

		return name;
	}

	public static VfsException operationFailure(String protocol, Throwable error) {
		if (error instanceof VfsException) return (VfsException) error;
		if (error instanceof SocketTimeoutException) {
			return new VfsException(protocol + " operation timed out", error);
		}
		if (error instanceof EOFException) {
			return new VfsException(protocol + " connection dropped or response was truncated", error);
		}
		return new VfsException(protocol + " operation failed", error);
	}

	private static VfsException malformed(String protocol, String detail) {
		return new VfsException(protocol + " server returned malformed data: " + detail);
	}
}
