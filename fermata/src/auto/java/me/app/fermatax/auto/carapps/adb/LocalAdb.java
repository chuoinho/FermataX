package me.app.fermatax.auto.carapps.adb;

import android.content.Context;
import android.os.Build;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.PairingConnectionCtx;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import static me.aap.utils.async.Completed.failed;

/** A short, loopback-only ADB session. No network work runs on the caller thread. */
public final class LocalAdb implements Closeable {
    private static final Object KEY_LOCK = new Object();
    private static final String HOST = "127.0.0.1";
    static final TransportFactory DEFAULT_TRANSPORT_FACTORY = ManagerTransport::new;
    private final File directory;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final TransportFactory factory;
    private final long timeoutMillis;
    private final Object stateLock = new Object();
    private Operation active;
    private boolean closed;

    public LocalAdb(Context context) {
        this(context.getFilesDir(), App.get().getExecutor(), App.get().getScheduler(),
                DEFAULT_TRANSPORT_FACTORY, 15000);
    }

    LocalAdb(File files, Executor executor, ScheduledExecutorService scheduler,
             TransportFactory factory, long timeoutMillis) {
        if (timeoutMillis <= 0 || timeoutMillis > 15000) throw new IllegalArgumentException("Invalid timeout");
        directory = new File(files, "adb");
        this.executor = executor;
        this.scheduler = scheduler;
        this.factory = factory;
        this.timeoutMillis = timeoutMillis;
    }

    public FutureSupplier<Void> pair(int port, String code) {
        if (code == null || !code.matches("[0-9]{6}"))
            return failed(new IllegalArgumentException("Invalid pairing code"));
        return submit(port, code, null);
    }

    public FutureSupplier<Void> runAndClose(int port, String command) {
        if (command == null || command.isEmpty() || command.indexOf('\0') >= 0)
            return failed(new IllegalArgumentException("Invalid shell command"));
        return submit(port, null, "shell:" + command);
    }

    private FutureSupplier<Void> submit(int port, String code, String destination) {
        if (port < 1 || port > 65535) return failed(new IllegalArgumentException("Invalid port"));
        Operation operation = new Operation();
        synchronized (stateLock) {
            if (closed || active != null) return failed(new IllegalStateException("ADB session unavailable"));
            active = operation;
        }
        try {
            operation.deadline = scheduler.schedule(() -> operation.abort(new TimeoutException()),
                    timeoutMillis, TimeUnit.MILLISECONDS);
            executor.execute(() -> operation.run(port, code, destination));
        } catch (RuntimeException error) {
            operation.abort(error);
            synchronized (stateLock) { if (active == operation) active = null; }
        }
        return operation.result;
    }

    public boolean hasKey() {
        synchronized (KEY_LOCK) { return new File(directory, "identity").isFile(); }
    }

    public void forgetKey() {
        synchronized (stateLock) {
            if (active != null) active.abort(new CancellationException());
            synchronized (KEY_LOCK) {
                try { Files.deleteIfExists(new File(directory, "identity").toPath()); }
                catch (IOException error) { throw new IllegalStateException("Unable to forget ADB identity", error); }
            }
        }
    }

    @Override public void close() {
        synchronized (stateLock) {
            closed = true;
            if (active != null) active.abort(new CancellationException());
        }
    }

    private Identity identity(Operation operation) throws Exception {
        synchronized (KEY_LOCK) {
            if (operation.result.isDone()) throw new CancellationException();
            File file = new File(directory, "identity");
            if (file.isFile()) {
                try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
                    int size = input.readInt();
                    if (size < 1 || size > 8192) throw new IOException("Invalid ADB identity");
                    byte[] encoded = new byte[size];
                    input.readFully(encoded);
                    PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
                    Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(input);
                    if (!((RSAPrivateKey) key).getModulus().equals(((RSAPublicKey) cert.getPublicKey()).getModulus()))
                        throw new IOException("Invalid ADB identity");
                    return new Identity(key, cert);
                }
            }
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Unable to create ADB directory");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair pair = generator.generateKeyPair();
            long now = System.currentTimeMillis();
            X500Name subject = new X500Name("CN=FermataX");
            Certificate cert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(subject, new BigInteger(160, new SecureRandom()).setBit(0),
                            new Date(now - 86400000L), new Date(now + 630720000000L), subject, pair.getPublic())
                            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            if (operation.result.isDone()) throw new CancellationException();
            File temporary = new File(directory, "identity.tmp");
            try {
                try (FileOutputStream output = new FileOutputStream(temporary);
                     DataOutputStream data = new DataOutputStream(output)) {
                    // The enclosing Android files directory is app-private. Set 0600 on POSIX too.
                    var permissions = Files.getFileAttributeView(temporary.toPath(),
                            java.nio.file.attribute.PosixFileAttributeView.class);
                    if (permissions != null) permissions.setPermissions(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                    byte[] encoded = pair.getPrivate().getEncoded();
                    data.writeInt(encoded.length);
                    data.write(encoded);
                    data.write(cert.getEncoded());
                    data.flush();
                    output.getFD().sync();
                }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary.toPath()); }
            return new Identity(pair.getPrivate(), cert);
        }
    }

    private final class Operation {
        final Socket socket = new Socket();
        volatile Thread worker;
        volatile ScheduledFuture<?> deadline;
        final java.util.concurrent.atomic.AtomicReference<Throwable> aborted = new java.util.concurrent.atomic.AtomicReference<>();
        final Promise<Void> result = new Promise<>() {
            @Override public boolean cancel(boolean interrupt) {
                boolean cancelled = super.cancel(interrupt);
                if (cancelled) abort(new CancellationException());
                return cancelled;
            }
        };

        void abort(Throwable error) {
            aborted.compareAndSet(null, error);
            try { socket.close(); } catch (IOException ignored) { }
            Thread running = worker;
            if (running != null) running.interrupt();
            result.completeExceptionally(aborted.get());
        }

        void run(int port, String code, String destination) {
            worker = Thread.currentThread();
            Throwable failure = null;
            try {
                Identity identity = identity(this);
                if (!result.isDone()) {
                    try (Transport transport = factory.create(HOST, socket, identity.key,
                            identity.certificate, (int) timeoutMillis)) {
                        if (result.isDone()) throw new CancellationException();
                        if (code != null) {
                            if (!transport.pair(port, code)) throw new AdbPairingRequiredException("ADB pairing is required");
                        }
                        else transport.run(port, destination);
                    }
                }
            } catch (AdbAuthenticationFailedException error) {
                failure = new AdbPairingRequiredException("ADB pairing is required");
            } catch (Throwable error) {
                failure = error;
            } finally {
                try { socket.close(); } catch (IOException error) { if (failure == null) failure = error; }
                ScheduledFuture<?> timer = deadline;
                if (timer != null) timer.cancel(false);
                synchronized (stateLock) { if (active == this) active = null; }
                worker = null;
                // Do not leak cancellation's interrupt into the shared application executor.
                Thread.interrupted();
                result.complete(null, aborted.get() == null ? failure : aborted.get());
            }
        }
    }

    private static final class Identity {
        final PrivateKey key;
        final Certificate certificate;
        Identity(PrivateKey key, Certificate certificate) { this.key = key; this.certificate = certificate; }
    }

    interface Transport extends Closeable {
        boolean pair(int port, String code) throws Exception;
        void run(int port, String destination) throws Exception;
    }
    interface TransportFactory {
        Transport create(String host, Socket socket, PrivateKey key, Certificate certificate,
                         int timeoutMillis) throws Exception;
    }

    /**
     * The label Android lists under Wireless debugging > Paired devices: model plus 4 hex chars of the public key hash,
     * so several installs can be told apart. It is only a label (pairing trusts the key), and at most 40 ASCII chars.
     */
    static String deviceName(Certificate certificate, String model) {
        String id;
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(certificate.getPublicKey().getEncoded());
            id = String.format("%02x%02x", hash[0], hash[1]);
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        String clean = model == null ? "" : model.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (clean.length() > 26) clean = clean.substring(0, 26);
        return clean.isEmpty() ? "FermataX " + id : "FermataX " + clean + " " + id;
    }

    static final class ManagerTransport extends AbsAdbConnectionManager implements Transport {
        private final Socket socket;
        private final PrivateKey key;
        private final Certificate certificate;
        private final int timeout;

        ManagerTransport(String host, Socket socket, PrivateKey key, Certificate certificate, int timeout) {
            if (!HOST.equals(host)) throw new IllegalArgumentException("Non-local ADB host");
            this.socket = socket;
            this.key = key;
            this.certificate = certificate;
            this.timeout = timeout;
            setApi(Build.VERSION.SDK_INT);
            setTimeout(timeout, TimeUnit.MILLISECONDS);
            setThrowOnUnauthorised(true);
        }

        @Override protected PrivateKey getPrivateKey() { return key; }
        @Override protected Certificate getCertificate() { return certificate; }
        @Override protected String getDeviceName() { return deviceName(certificate, Build.MODEL); }
        @Override protected AdbConnection createConnection(String host, int port) throws IOException {
            return new AdbConnection.Builder(HOST, port).setSocket(socket, timeout).setApi(getApi())
                    .setPrivateKey(key).setCertificate(certificate).setDeviceName(getDeviceName()).build();
        }
        @Override protected PairingConnectionCtx createPairingConnection(String host, int port, String code)
                throws Exception {
            return new PairingConnectionCtx(HOST, port, code.getBytes(StandardCharsets.UTF_8),
                    key, certificate, getDeviceName(), socket, timeout);
        }
        @Override public void run(int port, String destination) throws Exception {
            if (!connect(port)) throw new TimeoutException();
            try (AdbStream stream = openStream(destination)) {
                byte[] discard = new byte[1024];
                // Read the stream directly: -1 only for peer close, local/transport closure throws.
                while (stream.read(discard, 0, discard.length) != -1) { /* Drain without retaining output. */ }
            }
        }
        @Override public void close() throws IOException { disconnect(); }
    }
}
