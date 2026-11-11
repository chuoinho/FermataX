package me.app.fermatax.auto.carapps.adb;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.concurrent.*;

import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import me.aap.utils.async.FutureSupplier;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LocalAdbTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private File files;
    private Fake transport;
    private LocalAdb adb;

    @Before public void setUp() throws Exception {
        files = folder.newFolder();
        transport = new Fake();
        adb = create(transport, 3000);
    }

    @After public void tearDown() throws Exception {
        adb.close();
        executor.shutdownNow();
        scheduler.shutdownNow();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

    private LocalAdb create(Fake fake, long timeout) {
        return new LocalAdb(files, executor, scheduler, (host, socket, key, certificate, millis) -> {
            fake.host = host;
            fake.socket = socket;
            fake.key = key;
            fake.certificate = certificate;
            fake.timeout = millis;
            return fake;
        }, timeout);
    }

    private static Throwable failure(FutureSupplier<Void> future) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); }
        catch (ExecutionException | CancellationException ignored) { }
        return future.getFailure();
    }

    private static void succeeds(FutureSupplier<Void> future) throws Exception {
        assertNull("Operation must complete successfully", failure(future));
    }

    @Test public void identityIsRsa2048AndSurvivesAnotherInstance() throws Exception {
        assertFalse(adb.hasKey());
        succeeds(adb.runAndClose(32123, "true"));
        assertTrue(adb.hasKey());
        assertEquals(2048, ((RSAPrivateKey) transport.key).getModulus().bitLength());
        Fake second = new Fake();
        try (LocalAdb other = create(second, 3000)) {
            succeeds(other.runAndClose(32123, "true"));
            assertTrue("Restart must retain the existing private identity",
                    Arrays.equals(transport.key.getEncoded(), second.key.getEncoded()));
            assertTrue(Arrays.equals(transport.certificate.getEncoded(), second.certificate.getEncoded()));
        }
        assertEquals("Atomic identity storage leaves one completed file", 1, new File(files, "adb").list().length);
    }

    @Test public void deviceNameTellsInstallsApartAndIsStable() throws Exception {
        org.robolectric.shadows.ShadowBuild.setModel("Pixel 7");
        succeeds(adb.runAndClose(32123, "true"));
        String name = nameOf(transport);
        assertTrue(name, name.matches("FermataX-Pixel-7-[0-9a-f]{4}"));
        Fake second = new Fake();
        try (LocalAdb other = create(second, 3000)) {
            succeeds(other.runAndClose(32123, "true"));
            assertEquals("Same install, same label", name, nameOf(second));
        }
        adb.forgetKey();
        Fake third = new Fake();
        try (LocalAdb other = create(third, 3000)) {
            succeeds(other.runAndClose(32123, "true"));
            assertNotEquals("A new identity is a new pairing entry", name, nameOf(third));
        }
    }

    @Test public void deviceNameIsSafeAndShort() throws Exception {
        succeeds(adb.runAndClose(32123, "true"));
        String hostile = "Mo\u0000del \u00e9\n" + "x".repeat(200);
        String name = LocalAdb.deviceName(transport.certificate, hostile);
        assertTrue(name, name.length() <= 40);
        assertTrue(name, name.matches("FermataX-[A-Za-z0-9._-]+-[0-9a-f]{4}"));
        assertFalse("Settings shows only the first word", name.matches(".*\s.*"));
        assertTrue(LocalAdb.deviceName(transport.certificate, null).matches("FermataX-[0-9a-f]{4}"));
        assertTrue(LocalAdb.deviceName(transport.certificate, "\u00e9\u00e9").matches("FermataX-[0-9a-f]{4}"));
    }

    private static String nameOf(Fake fake) throws Exception {
        return new LocalAdb.ManagerTransport("127.0.0.1", new Socket(), fake.key, fake.certificate, 1000)
                .getDeviceName();
    }

    @Test public void shellRunsOffCallerThreadOnLoopbackAndCloses() throws Exception {
        Thread caller = Thread.currentThread();
        succeeds(adb.runAndClose(32123, "echo ready"));
        assertEquals("127.0.0.1", transport.host);
        assertEquals(32123, transport.port);
        assertEquals("shell:echo ready", transport.destination);
        assertNotSame(caller, transport.thread);
        assertTrue(transport.closed);
        assertTrue(transport.socket.isClosed());
    }

    @Test public void failedShellClosesAndPreservesOriginalError() throws Exception {
        IOException expected = new IOException("Transport failed");
        transport.error = expected;
        assertSame(expected, failure(adb.runAndClose(32123, "true")));
        assertTrue(transport.closed);
        assertTrue(transport.socket.isClosed());
    }

    @Test public void authenticationFailureRequestsPairingAndCloses() throws Exception {
        transport.error = new AdbAuthenticationFailedException();
        assertTrue(failure(adb.runAndClose(32123, "true")) instanceof AdbPairingRequiredException);
        assertTrue(transport.closed);
    }

    @Test public void upstreamPairingRequiredReachesCaller() throws Exception {
        AdbPairingRequiredException expected = new AdbPairingRequiredException("Pairing required");
        transport.error = expected;
        assertSame(expected, failure(adb.runAndClose(32123, "true")));
        assertTrue(transport.closed);
    }

    @Test public void pairingUsesLoopbackAndNeverKeepsTransportOpen() throws Exception {
        // Placeholder input is never a device pairing code and is never logged.
        succeeds(adb.pair(32124, "000000"));
        assertEquals("127.0.0.1", transport.host);
        assertEquals(32124, transport.port);
        assertTrue(transport.paired);
        assertTrue(transport.closed);
        assertTrue(transport.socket.isClosed());
    }

    @Test public void forgettingIdentityChangesNextSessionCredentials() throws Exception {
        succeeds(adb.runAndClose(32123, "true"));
        byte[] previous = transport.key.getEncoded();
        adb.forgetKey();
        assertFalse(adb.hasKey());
        Fake next = new Fake();
        try (LocalAdb other = create(next, 3000)) {
            succeeds(other.runAndClose(32123, "true"));
            assertFalse("Forgotten credentials must not return", Arrays.equals(previous, next.key.getEncoded()));
        }
    }

    @Test public void deadlineClosesSocketAndReleasesBlockedWorker() throws Exception {
        succeeds(adb.runAndClose(32123, "true"));
        Fake blocked = new Fake();
        blocked.block = true;
        try (LocalAdb timed = create(blocked, 200)) {
            FutureSupplier<Void> result = timed.runAndClose(32123, "true");
            assertTrue(blocked.started.await(2, TimeUnit.SECONDS));
            assertTrue(failure(result) instanceof TimeoutException);
            assertTrue(blocked.socket.isClosed());
            assertTrue("Timeout must terminate worker cleanup", blocked.finished.await(2, TimeUnit.SECONDS));
            assertTrue(blocked.closed);
        }
    }

    @Test public void concurrentOperationCannotReplaceActiveTransport() throws Exception {
        transport.block = true;
        FutureSupplier<Void> first = adb.runAndClose(32123, "true");
        assertTrue(transport.started.await(3, TimeUnit.SECONDS));
        assertTrue(failure(adb.runAndClose(32124, "true")) instanceof IllegalStateException);
        first.cancel(true);
        assertTrue(transport.finished.await(2, TimeUnit.SECONDS));
        assertTrue(transport.closed);
        assertTrue(transport.socket.isClosed());
    }

    @Test public void invalidPortAndCodeDoNotCreateIdentityOrTransport() throws Exception {
        assertTrue(failure(adb.runAndClose(0, "true")) instanceof IllegalArgumentException);
        assertTrue(failure(adb.pair(32124, "invalid")) instanceof IllegalArgumentException);
        assertFalse(adb.hasKey());
        assertNull(transport.socket);
    }

    @Test public void watchdogClosesRealStalledProtocolSocket() throws Exception { stalledServer(0); }
    @Test public void watchdogClosesRealStalledTlsSocket() throws Exception { stalledServer(1); }
    @Test public void watchdogClosesRealStalledOpenWithoutManagerLock() throws Exception { stalledServer(2); }

    private void stalledServer(int mode) throws Exception {
        succeeds(adb.runAndClose(32123, "true")); // Generate identity before the short network deadline.
        ExecutorService peerWorker = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Socket> owned = new java.util.concurrent.atomic.AtomicReference<>();
        // OPEN: a real 700 ms deadline raced connect, CNXN and OPEN; it fires only once the peer has read OPEN
        ManualDeadline manual = (mode == 2) ? new ManualDeadline() : null;
        CountDownLatch openRead = new CountDownLatch(1);
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"));
             LocalAdb real = new LocalAdb(files, executor, (manual != null) ? manual : scheduler, (host, socket, key, cert, timeout) -> {
                 owned.set(socket);
                 return LocalAdb.DEFAULT_TRANSPORT_FACTORY.create(host, socket, key, cert, timeout);
             }, 700)) {
            server.setSoTimeout(3000);
            Future<Boolean> disconnected = peerWorker.submit(() -> {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(3000);
                    java.io.InputStream input = peer.getInputStream();
                    if (mode == 2) {
                        readPacket(input); // Client CNXN.
                        java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(24)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        int command = 0x4e584e43;
                        header.putInt(command).putInt(0x01000001).putInt(1048576)
                                .putInt(0).putInt(0).putInt(command ^ -1);
                        peer.getOutputStream().write(header.array());
                        peer.getOutputStream().flush();
                        assertEquals("Fake peer must stall after an actual OPEN", 0x4e45504f, readPacket(input));
                        openRead.countDown();
                    }
                    while (input.read() != -1) { }
                    return true;
                }
            });
            FutureSupplier<Void> result = mode == 1 ? real.pair(server.getLocalPort(), "000000")
                    : real.runAndClose(server.getLocalPort(), "true");
            if (manual != null) {
                assertTrue("The client must send OPEN", openRead.await(5, TimeUnit.SECONDS));
                manual.fire();
            }
            assertTrue("Deadline must remain a timeout after socket shutdown", failure(result) instanceof TimeoutException);
            assertTrue("The watchdog must close the actual transport", owned.get().isClosed());
            assertTrue("The local peer must observe disconnection", disconnected.get(4, TimeUnit.SECONDS));
        } finally {
            peerWorker.shutdownNow();
            if (manual != null) manual.shutdownNow();
        }
    }

    /** Holds the deadline LocalAdb schedules until the test fires it; its own timer is a day away, only cancelled. */
    private static final class ManualDeadline extends ScheduledThreadPoolExecutor {
        private volatile Runnable deadline;

        ManualDeadline() { super(1); }

        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            deadline = command;
            return super.schedule(() -> { }, 1, TimeUnit.DAYS);
        }

        void fire() { deadline.run(); }
    }

    private static int readPacket(java.io.InputStream input) throws IOException {
        return readHeader(input).getInt(0);
    }

    private static java.nio.ByteBuffer readHeader(java.io.InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(24);
        if (bytes.length != 24) throw new IOException("Missing test packet");
        java.nio.ByteBuffer packet = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int length = packet.getInt(12);
        if (length < 0 || length > 8192 || input.readNBytes(length).length != length)
            throw new IOException("Invalid test packet");
        return packet;
    }

    private static byte[] packet(int command, int arg0, int arg1, byte[] payload) {
        int sum = 0;
        for (byte b : payload) sum += b & 0xff;
        return java.nio.ByteBuffer.allocate(24 + payload.length).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.length).putInt(sum)
                .putInt(~command).put(payload).array();
    }

    /** Real transport against a loopback peer that accepts the shell stream, then closes the stream or the socket. */
    private Throwable shellAgainstPeer(boolean output, long delayMillis, boolean reset, boolean closeStream)
            throws Exception {
        succeeds(adb.runAndClose(32123, "true")); // Generate identity before the network operation.
        ExecutorService peerWorker = Executors.newSingleThreadExecutor();
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"));
             LocalAdb real = new LocalAdb(files, executor, scheduler, LocalAdb.DEFAULT_TRANSPORT_FACTORY, 3000)) {
            server.setSoTimeout(3000);
            Future<?> served = peerWorker.submit(() -> {
                try (Socket peer = server.accept()) {
                    peer.setSoTimeout(3000);
                    java.io.InputStream input = peer.getInputStream();
                    java.io.OutputStream out = peer.getOutputStream();
                    readPacket(input); // Client CNXN.
                    out.write(packet(0x4e584e43, 0x01000001, 1048576, new byte[0]));
                    java.nio.ByteBuffer open = readHeader(input);
                    assertEquals(0x4e45504f, open.getInt(0));
                    int client = open.getInt(4);
                    out.write(packet(0x59414b4f, 1, client, new byte[0])); // OKAY
                    if (output) out.write(packet(0x45545257, 1, client, "output\n".getBytes()));
                    out.flush();
                    Thread.sleep(delayMillis); // Let the client block in read first.
                    if (closeStream) { // Peer-side CLSE, then wait for the client to disconnect.
                        out.write(packet(0x45534c43, 1, client, new byte[0]));
                        out.flush();
                        try { while (input.read() != -1) { } } catch (IOException ignored) { }
                    } else if (reset) peer.setSoLinger(true, 0); // Otherwise a plain FIN without CLSE.
                }
                return null;
            });
            Throwable failure = failure(real.runAndClose(server.getLocalPort(), "true"));
            served.get(4, TimeUnit.SECONDS);
            return failure;
        } finally { peerWorker.shutdownNow(); }
    }

    @Test public void peerClosingShellStreamAfterCommandIsSuccess() throws Exception {
        assertNull(shellAgainstPeer(false, 300, false, true));
    }

    @Test public void peerClosingShellStreamAfterOutputIsSuccess() throws Exception {
        assertNull(shellAgainstPeer(true, 300, false, true));
    }

    @Test public void socketResetMidDrainStillFails() throws Exception {
        assertTrue(shellAgainstPeer(false, 300, true, false) instanceof IOException);
    }

    @Test public void socketResetBeforeDrainStartsStillFails() throws Exception {
        assertTrue(shellAgainstPeer(false, 0, true, false) instanceof IOException);
    }

    @Test public void socketEofWithoutPeerCloseStillFails() throws Exception {
        assertTrue(shellAgainstPeer(true, 300, false, false) instanceof IOException);
    }

    private static final class Fake implements LocalAdb.Transport {
        String host, destination;
        int port, timeout;
        Socket socket;
        PrivateKey key;
        Certificate certificate;
        Thread thread;
        Exception error;
        boolean closed, paired, block;
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);

        private void execute(int port) throws Exception {
            this.port = port;
            thread = Thread.currentThread();
            started.countDown();
            if (block) new CountDownLatch(1).await();
            if (error != null) throw error;
        }
        public boolean pair(int port, String code) throws Exception { paired = true; execute(port); return true; }
        public void run(int port, String destination) throws Exception { this.destination = destination; execute(port); }
        public void close() { closed = true; finished.countDown(); }
    }
}
