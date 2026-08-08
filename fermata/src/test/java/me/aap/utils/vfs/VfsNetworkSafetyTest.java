package me.aap.utils.vfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.Test;

public class VfsNetworkSafetyTest {
	@Test
	public void rejectsTruncatedOrMissingDirectoryResponses() {
		assertMalformed(() -> VfsNetworkSafety.requireDirectoryListing("SMB", null));
		assertMalformed(() -> VfsNetworkSafety.requireEntry("SMB", null));
		assertMalformed(() -> VfsNetworkSafety.requireField("SFTP", "attributes", null));
	}

	@Test
	public void rejectsAdversarialDirectoryNamesBeforeTheyBecomePaths() throws Exception {
		for (String name : List.of("", "folder/child", "folder\\child", "bad\u0000name", "bad\nname")) {
			assertMalformed(() -> VfsNetworkSafety.requireEntryName("SFTP", name));
		}
		assertEquals(".", VfsNetworkSafety.requireEntryName("SMB", "."));
		assertEquals("..", VfsNetworkSafety.requireEntryName("SMB", ".."));
		assertEquals("valid-unicode-đ", VfsNetworkSafety.requireEntryName("SMB", "valid-unicode-đ"));
	}

	@Test
	public void mapsConnectionDropsTimeoutsAndProtocolErrorsToCatchableVfsFailures() {
		VfsException dropped = VfsNetworkSafety.operationFailure("SMB", new EOFException("cut"));
		assertTrue(dropped.getMessage().contains("dropped"));
		assertTrue(dropped.getCause() instanceof EOFException);

		VfsException timedOut = VfsNetworkSafety.operationFailure("SFTP", new SocketTimeoutException("late"));
		assertTrue(timedOut.getMessage().contains("timed out"));
		assertTrue(timedOut.getCause() instanceof SocketTimeoutException);

		IOException protocolError = new IOException("bad packet");
		VfsException failed = VfsNetworkSafety.operationFailure("SMB", protocolError);
		assertEquals("SMB operation failed", failed.getMessage());
		assertSame(protocolError, failed.getCause());
	}

	@Test
	public void preservesAlreadyNormalizedVfsFailures() {
		VfsException original = new VfsException("already safe");
		assertSame(original, VfsNetworkSafety.operationFailure("SFTP", original));
	}

	private static void assertMalformed(ThrowingRunnable action) {
		VfsException failure = assertThrows(VfsException.class, action::run);
		assertTrue(failure.getMessage().contains("malformed data"));
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
