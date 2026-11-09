package io.github.muntashirakon.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Android 13-15 block the platform Conscrypt's hidden exportKeyingMaterial, so pairing must run on the bundled
 * org.conscrypt (the TLS socket is its own class, nothing hidden is reflected) and not on the platform's.
 * The JVM stand-in for the Android artifact is conscrypt-openjdk-uber (test only).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class PairingConscryptTest {

    @Test public void pairingExportsItsKeyingMaterialFromTheBundledConscrypt() throws Exception {
        java.security.KeyPair pair;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Test");
        java.security.cert.Certificate cert = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.ONE, new Date(0), new Date(4102444800000L),
                        subject, pair.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        KeyPair key = new KeyPair(pair.getPrivate(), cert);

        SSLContext client = SslUtils.getSslContext(key);
        assertTrue("the platform's TLS (hidden-API blocked) was used", SslUtils.isCustomConscrypt());
        assertEquals("org.conscrypt.OpenSSLProvider", client.getProvider().getClass().getName());

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("k", pair.getPrivate(), new char[0], new java.security.cert.Certificate[]{cert});
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, new char[0]);
        SSLContext server = SSLContext.getInstance("TLSv1.3", client.getProvider());
        server.init(keys.getKeyManagers(), null, null);

        PairingConnectionCtx ctx = new PairingConnectionCtx("127.0.0.1", 1, new byte[0], key, "Test");
        Method export = PairingConnectionCtx.class.getDeclaredMethod("exportKeyingMaterial", SSLSocket.class, int.class);
        export.setAccessible(true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (SSLServerSocket listener = (SSLServerSocket) server.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<byte[]> peer = pool.submit((Callable<byte[]>) () -> {
                try (SSLSocket accepted = (SSLSocket) listener.accept()) {
                    accepted.setSoTimeout(10000);
                    accepted.startHandshake();
                    return (byte[]) export.invoke(ctx, accepted, PairingConnectionCtx.EXPORT_KEY_SIZE);
                }
            });
            try (Socket raw = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                 SSLSocket tls = (SSLSocket) client.getSocketFactory().createSocket(raw, "127.0.0.1", listener.getLocalPort(), true)) {
                tls.setSoTimeout(10000);
                tls.startHandshake();
                assertTrue(tls.getClass().getName(), tls.getClass().getName().startsWith("org.conscrypt."));

                byte[] mine = (byte[]) export.invoke(ctx, tls, PairingConnectionCtx.EXPORT_KEY_SIZE);

                assertEquals(PairingConnectionCtx.EXPORT_KEY_SIZE, mine.length);
                assertArrayEquals("both ends of the TLS session derive the same key", peer.get(10, TimeUnit.SECONDS), mine);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
