package io.github.muntashirakon.adb;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.*;

import javax.net.ssl.*;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class TransportLifetimeTest {
    private static KeyPair identity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        java.security.KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Test Client");
        Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                        new Date(0), new Date(4102444800000L), subject, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        return new KeyPair(pair.getPrivate(), certificate);
    }

    private static AdbConnection connection(Socket owned, int port, KeyPair key) throws Exception {
        AdbConnection.Builder builder = new AdbConnection.Builder("127.0.0.1", port)
                .setApi(33).setPrivateKey(key.getPrivateKey()).setCertificate(key.getCertificate());
        try {
            builder.getClass().getMethod("setSocket", Socket.class, int.class)
                    .invoke(builder, owned, 1000);
        } catch (NoSuchMethodException missing) {
            fail("Connection builder must accept the caller-owned cancellable socket");
        }
        return builder.build();
    }

    @Test public void pairingCloseBeforeStartIsSafeAndIdempotent() throws Exception {
        KeyPair key = identity();
        PairingConnectionCtx context = new PairingConnectionCtx("127.0.0.1", 1,
                new byte[0], key, "Test");
        try { context.close(); context.close(); }
        catch (RuntimeException error) { fail("Closing an unstarted pairing must not mask its failure"); }
    }

    @Test public void callerCanCloseSocketWhileConnectWaitsForProtocol() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             Socket owned = new Socket()) {
            server.setSoTimeout(2000);
            try (AdbConnection connection = connection(owned, server.getLocalPort(), identity());
                 Socket peer = server.accept()) {
                Future<?> pending = worker.submit(() -> {
                    try { connection.connect(10000, TimeUnit.MILLISECONDS, true); }
                    catch (Exception expected) { return; }
                    fail("Closed transport must not connect");
                });
                owned.close();
                pending.get(2, TimeUnit.SECONDS);
                connection.close();
                assertTrue("Socket ownership must survive connection creation", owned.isClosed());
            }
        } finally { worker.shutdownNow(); }
    }

    @Test public void callerCanCloseSocketDuringPairingTlsHandshake() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             Socket owned = new Socket()) {
            server.setSoTimeout(2000);
            KeyPair key = identity();
            PairingConnectionCtx context;
            try {
                context = PairingConnectionCtx.class.getConstructor(String.class, int.class,
                        byte[].class, java.security.PrivateKey.class, Certificate.class,
                        String.class, Socket.class, int.class).newInstance("127.0.0.1",
                        server.getLocalPort(), new byte[0], key.getPrivateKey(),
                        key.getCertificate(), "Test", owned, 1000);
            } catch (NoSuchMethodException missing) {
                fail("Pairing must accept the caller-owned cancellable socket");
                return;
            }
            try (PairingConnectionCtx pairing = context) {
                Future<?> pending = worker.submit(() -> {
                    try { pairing.start(); }
                    catch (Exception expected) { return; }
                    fail("Stalled TLS must fail after its owned socket closes");
                });
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(2000);
                    assertTrue("TLS client must have started before cancellation", peer.getInputStream().read() >= 0);
                    owned.close();
                    pending.get(2, TimeUnit.SECONDS);
                    pairing.close();
                    assertTrue(owned.isClosed());
                }
            }
        } finally { worker.shutdownNow(); }
    }

    @Test public void recreatedTlsContextPresentsCurrentIdentity() throws Exception {
        KeyPair first = identity();
        KeyPair current = identity();
        SslUtils.getSslContext(first);
        SSLContext client = SslUtils.getSslContext(current);
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        char[] password = new char[0];
        store.setKeyEntry("test", first.getPrivateKey(), password, new Certificate[]{first.getCertificate()});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, password);
        SSLContext tlsServer = SSLContext.getInstance("TLSv1.3");
        tlsServer.init(managers.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) { }
            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) { }
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        }}, null);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket) tlsServer.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(3000);
            server.setNeedClientAuth(true);
            Future<Boolean> observed = worker.submit(() -> {
                try (SSLSocket peer = (SSLSocket) server.accept()) {
                    peer.setSoTimeout(3000);
                    peer.startHandshake();
                    return Arrays.equals(current.getCertificate().getEncoded(),
                            peer.getSession().getPeerCertificates()[0].getEncoded());
                }
            });
            try (SSLSocket socket = (SSLSocket) client.getSocketFactory().createSocket("127.0.0.1", server.getLocalPort())) {
                socket.setSoTimeout(3000);
                socket.startHandshake();
                assertTrue("A recreated TLS session must present the current identity", observed.get(4, TimeUnit.SECONDS));
            }
        } finally { worker.shutdownNow(); }
    }
}
