package io.github.muntashirakon.adb;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Runs the real peer-info step with the real AES-GCM decrypt; only the transport streams and the
 * (native, JVM-unloadable) SPAKE2 context are replaced. The PAKE key is the one a wrong pairing code yields.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class PairingPeerInfoTest {
    private static final byte PEER_INFO = 1;
    private static final int MAX_PEER_INFO_SIZE = 1 << 13;

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static PairingConnectionCtx context(byte[] peerBytes, byte[] localKey) throws Exception {
        java.security.KeyPair pair;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Test Client");
        KeyPair key = new KeyPair(pair.getPrivate(), new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, java.math.BigInteger.ONE, new java.util.Date(0),
                        new java.util.Date(4102444800000L), subject, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()))));
        PairingConnectionCtx context = new PairingConnectionCtx("127.0.0.1", 1, new byte[0], key, "Test");

        // PairingAuthCtx only needs its derived AES key for peer-info encryption; the SPAKE2 context is native.
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Object auth = Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class.class)
                .invoke(unsafe, PairingAuthCtx.class);
        set(auth, "mSecretKey", localKey);
        set(context, "mPairingAuthCtx", auth);
        set(context, "mInputStream", new DataInputStream(new ByteArrayInputStream(peerBytes)));
        set(context, "mOutputStream", new DataOutputStream(new ByteArrayOutputStream()));
        return context;
    }

    private static byte[] frame(byte[] payload) {
        return ByteBuffer.allocate(6 + payload.length).put((byte) 1).put(PEER_INFO)
                .putInt(payload.length).put(payload).array();
    }

    private static Throwable peerInfoFailure(PairingConnectionCtx context) throws Exception {
        Method method = PairingConnectionCtx.class.getDeclaredMethod("doExchangePeerInfo");
        method.setAccessible(true);
        try {
            method.invoke(context);
        } catch (InvocationTargetException failure) {
            return failure.getCause();
        }
        return null;
    }

    @Test public void peerInfoSealedWithAnotherKeyIsAnAuthenticationFailure() throws Exception {
        // A peer that derived its key from a different pairing code seals its peer info with a different key.
        byte[] peerKey = new byte[16], ourKey = new byte[16];
        Arrays.fill(peerKey, (byte) 1);
        Arrays.fill(ourKey, (byte) 2);
        PairingConnectionCtx peerSide = context(new byte[0], peerKey);
        Field authField = PairingConnectionCtx.class.getDeclaredField("mPairingAuthCtx");
        authField.setAccessible(true);
        PairingAuthCtx peer = (PairingAuthCtx) authField.get(peerSide);
        byte[] sealed = peer.encrypt(new byte[MAX_PEER_INFO_SIZE]);

        // Not closed: the stand-in auth context has no native SPAKE2 state to destroy.
        Throwable failure = peerInfoFailure(context(frame(sealed), ourKey));
        assertTrue("Wrong pairing code must surface as an authentication failure, was " + failure,
                failure instanceof AdbAuthenticationFailedException);
    }

    @Test public void truncatedPeerInfoStaysAnOrdinaryIoFailure() throws Exception {
        byte[] key = new byte[16];
        assertTrue(peerInfoFailure(context(new byte[]{1, PEER_INFO}, key)) instanceof EOFException);
    }
}
