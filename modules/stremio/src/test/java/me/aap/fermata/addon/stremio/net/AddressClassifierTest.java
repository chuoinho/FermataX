package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;

import java.net.Inet6Address;
import java.net.InetAddress;

import org.junit.Test;

public class AddressClassifierTest {
	@Test
	public void classifiesIpv4AddressFamilies() throws Exception {
		assertKind(AddressKind.PUBLIC, "8.8.8.8");
		assertKind(AddressKind.PRIVATE, "10.1.2.3");
		assertKind(AddressKind.PRIVATE, "172.31.255.1");
		assertKind(AddressKind.PRIVATE, "192.168.1.1");
		assertKind(AddressKind.RESERVED, "100.64.0.1");
		assertKind(AddressKind.RESERVED, "100.127.255.254");
		assertKind(AddressKind.PUBLIC, "100.63.255.255");
		assertKind(AddressKind.PUBLIC, "100.128.0.1");
		assertKind(AddressKind.LOOPBACK, "127.2.3.4");
		assertKind(AddressKind.LINK_LOCAL, "169.254.1.1");
		assertKind(AddressKind.MULTICAST, "239.1.2.3");
		assertKind(AddressKind.UNSPECIFIED, "0.0.0.0");
	}

	@Test
	public void classifiesIpv6AddressFamilies() throws Exception {
		assertKind(AddressKind.PUBLIC, "2001:4860:4860::8888");
		assertKind(AddressKind.PRIVATE, "fd12:3456::1");
		assertKind(AddressKind.LINK_LOCAL, "fe80::1");
		assertKind(AddressKind.LOOPBACK, "::1");
		assertKind(AddressKind.MULTICAST, "ff02::1");
		assertKind(AddressKind.UNSPECIFIED, "::");
	}

	@Test
	public void classifiesIpv4MappedIpv6BeforeIpv6Rules() throws Exception {
		byte[] privateMapped = mapped(10, 0, 0, 7);
		byte[] metadataMapped = mapped(169, 254, 169, 254);
		assertEquals(AddressKind.PRIVATE,
				AddressClassifier.classify(Inet6Address.getByAddress(null, privateMapped, -1)));
		assertEquals(AddressKind.CLOUD_METADATA,
				AddressClassifier.classify(Inet6Address.getByAddress(null, metadataMapped, -1)));
	}

	@Test
	public void classifiesDeprecatedIpv4CompatibleIpv6() throws Exception {
		assertEquals(AddressKind.LOOPBACK, classifyIpv6("::127.0.0.1"));
		assertEquals(AddressKind.PRIVATE, classifyIpv6("::10.0.0.1"));
		assertEquals(AddressKind.CLOUD_METADATA, classifyIpv6("::169.254.169.254"));
	}

	@Test
	public void classifiesEmbeddedIpv4In6to4TeredoAndNat64() throws Exception {
		assertEquals(AddressKind.LOOPBACK, classifyIpv6("2002:7f00:0001::"));
		assertEquals(AddressKind.PRIVATE, classifyIpv6("2002:0a00:0001::"));
		assertEquals(AddressKind.PUBLIC, classifyIpv6("2002:0808:0808::"));

		assertEquals(AddressKind.LOOPBACK,
				classifyIpv6("2001:0000:0808:0808:0000:0000:80ff:fffe"));
		assertEquals(AddressKind.PRIVATE,
				classifyIpv6("2001:0000:0808:0808:0000:0000:f5ff:fffe"));
		assertEquals(AddressKind.PRIVATE,
				classifyIpv6("2001:0000:0a00:0001:0000:0000:f7f7:f7f7"));

		assertEquals(AddressKind.LOOPBACK, classifyIpv6("64:ff9b::127.0.0.1"));
		assertEquals(AddressKind.PRIVATE, classifyIpv6("64:ff9b::10.0.0.1"));
	}

	@Test
	public void classifiesDocumentationBenchmarkAndReservedRanges() throws Exception {
		for (String address : new String[]{"0.0.0.1", "192.0.0.8", "192.0.2.1",
				"192.88.99.1", "198.18.0.1", "198.51.100.2", "203.0.113.3", "240.0.0.1"}) {
			assertKind(AddressKind.RESERVED, address);
		}
		assertEquals(AddressKind.RESERVED, classifyIpv6("2001:db8::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("3fff::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("100::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("100:0:0:1::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("2001:2::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("64:ff9b:1::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("5f00::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("4000::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("fec0::1"));
		assertEquals(AddressKind.RESERVED, classifyIpv6("2001:10::1"));
	}

	@Test
	public void recognizesCloudMetadataAddressesBeforeBroaderRanges() throws Exception {
		assertKind(AddressKind.CLOUD_METADATA, "169.254.169.254");
		assertKind(AddressKind.CLOUD_METADATA, "169.254.170.2");
		assertKind(AddressKind.CLOUD_METADATA, "100.100.100.200");
		assertKind(AddressKind.CLOUD_METADATA, "168.63.129.16");
		assertKind(AddressKind.CLOUD_METADATA, "192.0.0.192");
	}

	private static void assertKind(AddressKind expected, String address) throws Exception {
		assertEquals(expected, AddressClassifier.classify(InetAddress.getByName(address)));
	}

	private static AddressKind classifyIpv6(String address) throws Exception {
		byte[] bytes = InetAddress.getByName(address).getAddress();
		if (bytes.length == 4) {
			byte[] compatible = new byte[16];
			System.arraycopy(bytes, 0, compatible, 12, bytes.length);
			bytes = compatible;
		}
		return AddressClassifier.classify(Inet6Address.getByAddress(null, bytes, -1));
	}

	private static byte[] mapped(int a, int b, int c, int d) {
		byte[] bytes = new byte[16];
		bytes[10] = (byte) 0xff;
		bytes[11] = (byte) 0xff;
		bytes[12] = (byte) a;
		bytes[13] = (byte) b;
		bytes[14] = (byte) c;
		bytes[15] = (byte) d;
		return bytes;
	}
}
