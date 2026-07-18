package me.aap.fermata.provider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

public class FermataContentProviderSecurityTest {
	private static final String TOKEN =
			"00112233445566778899aabbccddeeff0011223344556677";

	@Test
	public void publicRequestAcceptsOnlyExactOpaqueImageToken() {
		assertNotNull(parse("/image/" + TOKEN, null, null));
		assertNull(FermataContentProvider.parseImageRequest("app.authority", "file",
				"app.authority", "/image/" + TOKEN, null, null));
		assertNull(FermataContentProvider.parseImageRequest("app.authority", "content",
				"other.authority", "/image/" + TOKEN, null, null));
		assertNull(parse("/image/" + TOKEN + "/extra", null, null));
		assertNull(parse("/image/%30" + TOKEN.substring(1), null, null));
		assertNull(parse("/image/" + TOKEN, "source=http://127.0.0.1", null));
		assertNull(parse("/image/" + TOKEN, null, "file:///private"));
	}

	@Test
	public void legacyEncodedSourcesCannotBecomeProviderPaths() {
		String[] dangerousSources = {
				"file:///data/user/0/me.app.fermataX.auto/shared_prefs/fermata.xml",
				"content://settings/secure",
				"http://127.0.0.1:8080/admin",
				"https://169.254.169.254/latest/meta-data"
		};

		for (String source : dangerousSources) {
			String encoded = Base64.getUrlEncoder().encodeToString(
					source.getBytes(StandardCharsets.US_ASCII));
			assertFalse(FermataContentProvider.isValidToken(encoded));
			assertNull(parse("/image/" + encoded, null, null));
		}
	}

	@Test
	public void tokenValidationRejectsTraversalAndMixedCase() {
		assertTrue(FermataContentProvider.isValidToken(TOKEN));
		assertFalse(FermataContentProvider.isValidToken("../" + TOKEN));
		assertFalse(FermataContentProvider.isValidToken(TOKEN.toUpperCase()));
		assertFalse(FermataContentProvider.isValidToken(TOKEN.substring(1)));
	}

	@Test
	public void privateAndSpecialNetworkAddressesAreRejected() throws Exception {
		String[] blocked = {
				"0.0.0.0", "127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.2",
				"172.16.0.1", "192.168.1.1", "198.18.0.1", "224.0.0.1", "::1",
				"fe80::1", "fc00::1", "2001:db8::1"
		};
		for (String address : blocked) {
			assertFalse(address, FermataContentProvider.isSafeRemoteAddress(
					InetAddress.getByName(address)));
		}
	}

	@Test
	public void publicNetworkAddressesRemainAvailableForArtwork() throws Exception {
		assertTrue(FermataContentProvider.isSafeRemoteAddress(InetAddress.getByName("8.8.8.8")));
		assertTrue(FermataContentProvider.isSafeRemoteAddress(
				InetAddress.getByName("2606:4700:4700::1111")));
	}

	@Test
	public void remoteConnectionIsPinnedToTheValidatedAddressAndPort() throws Exception {
		InetAddress publicAddress = InetAddress.getByName("8.8.8.8");
		InetSocketAddress https = FermataContentProvider.selectRemoteAddress(
				new URL("https://example.com/art.png"), new InetAddress[]{publicAddress});
		InetSocketAddress custom = FermataContentProvider.selectRemoteAddress(
				new URL("http://example.com:8080/art.png"), new InetAddress[]{publicAddress});

		assertEquals(publicAddress, https.getAddress());
		assertEquals(443, https.getPort());
		assertEquals(publicAddress, custom.getAddress());
		assertEquals(8080, custom.getPort());
	}

	@Test(expected = java.io.IOException.class)
	public void anyPrivateDnsAnswerRejectsTheWholeRemoteTarget() throws Exception {
		FermataContentProvider.selectRemoteAddress(new URL("https://example.com/art.png"),
				new InetAddress[]{InetAddress.getByName("8.8.8.8"),
						InetAddress.getByName("127.0.0.1")});
	}

	@Test
	public void bitmapSamplingAlwaysStartsAtAValidPositivePowerOfTwo() {
		assertEquals(1, FermataContentProvider.sampleSize(512, 512, 512));
		assertEquals(2, FermataContentProvider.sampleSize(2048, 1024, 512));
		assertEquals(4, FermataContentProvider.sampleSize(4096, 2048, 512));
		assertTrue(FermataContentProvider.sampleSize(Integer.MAX_VALUE,
				Integer.MAX_VALUE, 512) > 0);
	}

	private static FermataContentProvider.UriRequest parse(String path, String query,
			String fragment) {
		return FermataContentProvider.parseImageRequest("app.authority", "content",
				"app.authority", path, query, fragment);
	}
}
