package me.aap.fermata.addon.stremio.net;

import java.net.InetAddress;
import java.util.Objects;

public final class AddressClassifier {
	private AddressClassifier() {
	}

	public static AddressKind classify(InetAddress address) {
		Objects.requireNonNull(address, "address");
		byte[] bytes = address.getAddress();
		if (bytes.length == 4) return classifyIpv4(bytes);
		if (bytes.length != 16) throw new IllegalArgumentException("Unsupported address length");

		if (isIpv4Mapped(bytes)) {
			return classifyEmbeddedIpv4(bytes, 12, false);
		}
		if (allZero(bytes)) return AddressKind.UNSPECIFIED;
		if (isIpv6Loopback(bytes)) return AddressKind.LOOPBACK;
		if (isIpv4Compatible(bytes)) return classifyEmbeddedIpv4(bytes, 12, false);
		if (is6to4(bytes)) return classifyEmbeddedIpv4(bytes, 2, false);
		if (isTeredo(bytes)) {
			AddressKind server = classifyEmbeddedIpv4(bytes, 4, false);
			AddressKind client = classifyEmbeddedIpv4(bytes, 12, true);
			return moreRestricted(server, client);
		}
		if (isNat64WellKnown(bytes)) return classifyEmbeddedIpv4(bytes, 12, false);
		if (isNat64LocalUse(bytes) || isIpv6DiscardOnly(bytes) ||
				isIpv6Dummy(bytes) || isIpv6Benchmark(bytes) ||
				isIpv6Documentation(bytes) || isOrchid(bytes) ||
				isSrv6Sid(bytes)) return AddressKind.RESERVED;

		int first = unsigned(bytes[0]);
		int second = unsigned(bytes[1]);
		if (first == 0xff) return AddressKind.MULTICAST;
		if ((first == 0xfe) && ((second & 0xc0) == 0x80)) return AddressKind.LINK_LOCAL;
		if ((first & 0xfe) == 0xfc) return AddressKind.PRIVATE;
		if ((first == 0xfe) && ((second & 0xc0) == 0xc0)) return AddressKind.RESERVED;
		return isGlobalUnicast(bytes) ? AddressKind.PUBLIC : AddressKind.RESERVED;
	}

	private static AddressKind classifyIpv4(byte[] bytes) {
		int a = unsigned(bytes[0]);
		int b = unsigned(bytes[1]);
		int c = unsigned(bytes[2]);
		int d = unsigned(bytes[3]);

		if (isCloudMetadata(a, b, c, d)) return AddressKind.CLOUD_METADATA;
		if ((a == 0) && (b == 0) && (c == 0) && (d == 0)) return AddressKind.UNSPECIFIED;
		if (a == 0) return AddressKind.RESERVED;
		if (a == 127) return AddressKind.LOOPBACK;
		if ((a == 169) && (b == 254)) return AddressKind.LINK_LOCAL;
		if ((a >= 224) && (a <= 239)) return AddressKind.MULTICAST;
		if ((a == 10) || ((a == 172) && (b >= 16) && (b <= 31)) ||
				((a == 192) && (b == 168))) {
			return AddressKind.PRIVATE;
		}
		if (((a == 100) && ((b & 0xc0) == 0x40)) ||
				((a == 192) && (b == 0) && (c == 0)) ||
				((a == 192) && (b == 0) && (c == 2)) ||
				((a == 192) && (b == 88) && (c == 99)) ||
				((a == 198) && ((b == 18) || (b == 19))) ||
				((a == 198) && (b == 51) && (c == 100)) ||
				((a == 203) && (b == 0) && (c == 113)) || (a >= 240)) {
			return AddressKind.RESERVED;
		}
		return AddressKind.PUBLIC;
	}

	private static boolean isCloudMetadata(int a, int b, int c, int d) {
		return ((a == 169) && (b == 254) && (c == 169) && (d == 254)) ||
				((a == 169) && (b == 254) && (c == 170) && (d == 2)) ||
				((a == 100) && (b == 100) && (c == 100) && (d == 200)) ||
				((a == 168) && (b == 63) && (c == 129) && (d == 16)) ||
				((a == 192) && (b == 0) && (c == 0) && (d == 192));
	}

	private static boolean isIpv4Mapped(byte[] bytes) {
		for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
		return (bytes[10] == (byte) 0xff) && (bytes[11] == (byte) 0xff);
	}

	private static boolean isIpv4Compatible(byte[] bytes) {
		for (int i = 0; i < 12; i++) if (bytes[i] != 0) return false;
		return true;
	}

	private static boolean is6to4(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x20) && (unsigned(bytes[1]) == 0x02);
	}

	private static boolean isTeredo(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x20) && (unsigned(bytes[1]) == 0x01) &&
				(bytes[2] == 0) && (bytes[3] == 0);
	}

	private static boolean isNat64WellKnown(byte[] bytes) {
		if ((bytes[0] != 0) || (unsigned(bytes[1]) != 0x64) ||
				(unsigned(bytes[2]) != 0xff) || (unsigned(bytes[3]) != 0x9b)) return false;
		for (int i = 4; i < 12; i++) if (bytes[i] != 0) return false;
		return true;
	}

	private static boolean isNat64LocalUse(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x00) && (unsigned(bytes[1]) == 0x64) &&
				(unsigned(bytes[2]) == 0xff) && (unsigned(bytes[3]) == 0x9b) &&
				(unsigned(bytes[4]) == 0x00) && (unsigned(bytes[5]) == 0x01);
	}

	private static boolean isIpv6DiscardOnly(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x01) && (unsigned(bytes[1]) == 0x00) &&
				(bytes[2] == 0) && (bytes[3] == 0) && (bytes[4] == 0) && (bytes[5] == 0) &&
				(bytes[6] == 0) && (bytes[7] == 0);
	}

	private static boolean isIpv6Dummy(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x01) && (unsigned(bytes[1]) == 0x00) &&
				(bytes[2] == 0) && (bytes[3] == 0) && (bytes[4] == 0) && (bytes[5] == 0) &&
				(bytes[6] == 0) && (bytes[7] == 1);
	}

	private static boolean isIpv6Benchmark(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x20) && (unsigned(bytes[1]) == 0x01) &&
				(unsigned(bytes[2]) == 0x00) && (unsigned(bytes[3]) == 0x02) &&
				(bytes[4] == 0) && (bytes[5] == 0);
	}

	private static boolean isIpv6Documentation(byte[] bytes) {
		return ((unsigned(bytes[0]) == 0x20) && (unsigned(bytes[1]) == 0x01) &&
				(unsigned(bytes[2]) == 0x0d) && (unsigned(bytes[3]) == 0xb8)) ||
				((unsigned(bytes[0]) == 0x3f) && ((unsigned(bytes[1]) & 0xf0) == 0xf0));
	}

	private static boolean isOrchid(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x20) && (unsigned(bytes[1]) == 0x01) &&
				(bytes[2] == 0) && (((unsigned(bytes[3]) & 0xf0) == 0x10) ||
				((unsigned(bytes[3]) & 0xf0) == 0x20));
	}

	private static boolean isSrv6Sid(byte[] bytes) {
		return (unsigned(bytes[0]) == 0x5f) && (bytes[1] == 0);
	}

	private static boolean isGlobalUnicast(byte[] bytes) {
		return (unsigned(bytes[0]) & 0xe0) == 0x20;
	}

	private static AddressKind classifyEmbeddedIpv4(byte[] bytes, int offset, boolean inverted) {
		byte[] ipv4 = new byte[4];
		for (int i = 0; i < ipv4.length; i++) {
			ipv4[i] = inverted ? (byte) ~bytes[offset + i] : bytes[offset + i];
		}
		return classifyIpv4(ipv4);
	}

	private static AddressKind moreRestricted(AddressKind first, AddressKind second) {
		if (first == AddressKind.PUBLIC) return second;
		if (second == AddressKind.PUBLIC) return first;
		return (restrictionRank(first) >= restrictionRank(second)) ? first : second;
	}

	private static int restrictionRank(AddressKind kind) {
		return switch (kind) {
			case CLOUD_METADATA -> 7;
			case LOOPBACK -> 6;
			case LINK_LOCAL -> 5;
			case UNSPECIFIED -> 4;
			case MULTICAST -> 3;
			case RESERVED -> 2;
			case PRIVATE -> 1;
			case PUBLIC -> 0;
		};
	}

	private static boolean allZero(byte[] bytes) {
		for (byte value : bytes) if (value != 0) return false;
		return true;
	}

	private static boolean isIpv6Loopback(byte[] bytes) {
		for (int i = 0; i < 15; i++) if (bytes[i] != 0) return false;
		return bytes[15] == 1;
	}

	private static int unsigned(byte value) {
		return value & 0xff;
	}
}
