package me.aap.utils.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import me.aap.utils.net.NetHandler;
import me.aap.utils.net.NetServer;
import me.aap.utils.net.TlsTrustPolicy;
import me.aap.utils.misc.TestUtils;

class SecurityUtilsTlsTest {
	private NetHandler handler;
	private NetServer server;

	@BeforeAll
	static void setUpClass() {
		TestUtils.enableTestMode();
	}

	@BeforeEach
	void setUp() throws Exception {
		handler = NetHandler.create(o -> { });
		server = handler.bind(o -> {
			o.ssl = true;
			o.sslEngine = SecurityUtils::createServerSslEngine;
			o.handler = channel -> channel.write(ByteBuffer.wrap(new byte[]{42}));
		}).get(5, TimeUnit.SECONDS);
	}

	@AfterEach
	void tearDown() {
		if (server != null) server.close();
		if (handler != null) handler.close();
	}

	@Test
	void strictRejectsSelfSignedCertificate() {
		assertThrows(ExecutionException.class, () -> connect("localhost", TlsTrustPolicy.STRICT));
	}

	@Test
	void userSourcePolicyAcceptsSelfSignedCertificate() throws Exception {
		assertEquals(42, connect("localhost", TlsTrustPolicy.TRUST_ALL_USER_SOURCE));
	}

	@Test
	void strictRejectsTrustedCertificateForWrongHostname() throws Exception {
		SSLContext trustedContext = SSLContext.getInstance("TLS");
		trustedContext.init(null, new TrustManager[]{new AcceptAllTrustManager()}, new SecureRandom());
		InetSocketAddress address = (InetSocketAddress) server.getBindAddress();

		assertThrows(ExecutionException.class, () -> handler.connect(o -> {
			o.address = address;
			o.host = "wrong-host.example.invalid";
			o.port = address.getPort();
			o.ssl = true;
			o.tlsTrustPolicy = TlsTrustPolicy.STRICT;
			o.sslEngine = (host, port) -> SecurityUtils.createClientSslEngine(
					host, port, TlsTrustPolicy.STRICT, trustedContext);
		}).then(channel -> channel.read()).get(5, TimeUnit.SECONDS));
	}

	private int connect(String peerHost, TlsTrustPolicy policy) throws Exception {
		InetSocketAddress address = (InetSocketAddress) server.getBindAddress();
		ByteBuffer data = handler.connect(o -> {
			o.address = address;
			o.host = peerHost;
			o.port = address.getPort();
			o.ssl = true;
			o.tlsTrustPolicy = policy;
		}).then(channel -> channel.read()).get(5, TimeUnit.SECONDS);
		return data.get() & 0xff;
	}

	private static final class AcceptAllTrustManager implements X509TrustManager {
		@Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
		@Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
		@Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
	}
}
