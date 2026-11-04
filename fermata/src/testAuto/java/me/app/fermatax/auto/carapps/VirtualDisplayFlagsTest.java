package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VirtualDisplayFlagsTest {

	@Test
	public void sdk28FlagsAre0x014B() {
		int flags = VirtualDisplayFlags.forSdk(28);
		assertEquals(0x014B, flags);
		assertEquals(VirtualDisplayFlags.FLAG_PUBLIC, flags & VirtualDisplayFlags.FLAG_PUBLIC);
		assertEquals(VirtualDisplayFlags.FLAG_PRESENTATION, flags & VirtualDisplayFlags.FLAG_PRESENTATION);
		assertEquals(VirtualDisplayFlags.FLAG_OWN_CONTENT_ONLY, flags & VirtualDisplayFlags.FLAG_OWN_CONTENT_ONLY);
		assertEquals(VirtualDisplayFlags.FLAG_SUPPORTS_TOUCH, flags & VirtualDisplayFlags.FLAG_SUPPORTS_TOUCH);
		assertEquals(VirtualDisplayFlags.FLAG_DESTROY_CONTENT_ON_REMOVAL, flags & VirtualDisplayFlags.FLAG_DESTROY_CONTENT_ON_REMOVAL);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_TRUSTED);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_ALWAYS_UNLOCKED);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_TOUCH_FEEDBACK_DISABLED);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_ROTATES_WITH_CONTENT);
	}

	// AOSP DisplayManager.java: TRUSTED (1<<10) first appears in android11-release, OWN_DISPLAY_GROUP (1<<11)
	// in android12-release, ALWAYS_UNLOCKED and TOUCH_FEEDBACK_DISABLED in android13-release.
	@Test
	public void sdk29HasNoBitNewerThanAndroid10() {
		assertEquals(0x014B, VirtualDisplayFlags.forSdk(29));
	}

	@Test
	public void sdk30AddsOnlyTrusted() {
		int flags = VirtualDisplayFlags.forSdk(30);
		assertEquals(0x054B, flags);
		assertEquals(VirtualDisplayFlags.FLAG_TRUSTED, flags & VirtualDisplayFlags.FLAG_TRUSTED);
		assertEquals(0, flags & VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP);
	}

	@Test
	public void sdk31And32AddOwnDisplayGroup() {
		for (int sdk = 31; sdk <= 32; sdk++) {
			int flags = VirtualDisplayFlags.forSdk(sdk);
			assertEquals("SDK " + sdk, 0x0D4B, flags);
			assertEquals(VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP, flags & VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP);
			assertEquals(0, flags & (VirtualDisplayFlags.FLAG_ALWAYS_UNLOCKED | VirtualDisplayFlags.FLAG_TOUCH_FEEDBACK_DISABLED));
		}
	}

	@Test
	public void sdk33FlagsAre0x3D4B() {
		assertEquals(0x3D4B, VirtualDisplayFlags.forSdk(33));
	}

	@Test
	public void sdk34AndAboveFlagsAre0x7D4B() {
		assertEquals(0x7D4B, VirtualDisplayFlags.forSdk(34));
		assertEquals(0x7D4B, VirtualDisplayFlags.forSdk(35));
		assertEquals(0x7D4B, VirtualDisplayFlags.forSdk(36));
	}

	@Test
	public void neverRotatesWithContent() {
		for (int sdk = 28; sdk <= 37; sdk++) {
			int flags = VirtualDisplayFlags.forSdk(sdk);
			assertEquals("SDK " + sdk + " must never have ROTATES_WITH_CONTENT (0x80) flag set",
					0, flags & 0x80);
		}
	}

	@Test
	public void constantsMatchAosp() {
		assertEquals(0x1, VirtualDisplayFlags.FLAG_PUBLIC);
		assertEquals(0x2, VirtualDisplayFlags.FLAG_PRESENTATION);
		assertEquals(0x8, VirtualDisplayFlags.FLAG_OWN_CONTENT_ONLY);
		assertEquals(0x40, VirtualDisplayFlags.FLAG_SUPPORTS_TOUCH);
		assertEquals(0x80, VirtualDisplayFlags.FLAG_ROTATES_WITH_CONTENT);
		assertEquals(0x100, VirtualDisplayFlags.FLAG_DESTROY_CONTENT_ON_REMOVAL);
		assertEquals(0x400, VirtualDisplayFlags.FLAG_TRUSTED);
		assertEquals(0x800, VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP);
		assertEquals(0x1000, VirtualDisplayFlags.FLAG_ALWAYS_UNLOCKED);
		assertEquals(0x2000, VirtualDisplayFlags.FLAG_TOUCH_FEEDBACK_DISABLED);
		assertEquals(0x4000, VirtualDisplayFlags.FLAG_OWN_FOCUS);
	}

	@Test
	public void virtualDeviceFlagsAreTheOnesProvenOnThePhone() {
		assertEquals(0xF549, VirtualDisplayFlags.forVirtualDevice(36));
	}

	@Test
	public void virtualDeviceDisplayJoinsTheDeviceGroupNotAnOwnOne() {
		// the system rejects both together, and ignores the own group flag on Android 16
		assertEquals(0, VirtualDisplayFlags.forVirtualDevice(36) & VirtualDisplayFlags.FLAG_OWN_DISPLAY_GROUP);
		assertEquals(0x8000, VirtualDisplayFlags.FLAG_DEVICE_DISPLAY_GROUP);
	}

	@Test
	public void virtualDeviceIsNotTriedBeforeAndroid16() {
		// its builder has no screen off timeout there, and a failed try would still leave an association behind
		assertFalse(VirtualDisplayFlags.usesVirtualDevice(33));
		assertFalse(VirtualDisplayFlags.usesVirtualDevice(34));
		assertFalse(VirtualDisplayFlags.usesVirtualDevice(35));
		assertTrue(VirtualDisplayFlags.usesVirtualDevice(36));
	}

	@Test
	public void ownGroupNeedsAWakeLockBeforeAndroid16() {
		// no per group timeout there, so without the lock the car sleeps with the phone
		assertTrue(VirtualDisplayFlags.needsDisplayWakeLock(33, 1));
		assertTrue(VirtualDisplayFlags.needsDisplayWakeLock(34, 1));
		assertTrue(VirtualDisplayFlags.needsDisplayWakeLock(35, 1));
	}

	@Test
	public void sharedGroupGetsNoWakeLock() {
		// the lock would hold the phone screen on: an untrusted display stays in the phone's group
		assertFalse(VirtualDisplayFlags.needsDisplayWakeLock(35, 0));
	}

	@Test
	public void android16PlainDisplayGetsNoWakeLock() {
		// its own group flag is ignored there, the lock would not separate anything
		assertFalse(VirtualDisplayFlags.needsDisplayWakeLock(36, 1));
	}

	@Test
	public void droppingTrustedKeepsEveryOtherFlag() {
		assertEquals(0x794B, VirtualDisplayFlags.forSdk(34) & ~VirtualDisplayFlags.FLAG_TRUSTED);
		assertEquals(0x394B, VirtualDisplayFlags.forSdk(33) & ~VirtualDisplayFlags.FLAG_TRUSTED);
	}

	// Display.getFlags() uses Display.FLAG_TRUSTED = 1 << 7 (Display.java, android11..16-release).
	// 1 << 10 there is FLAG_TOUCH_FEEDBACK_DISABLED, which this server also requests: it must not count.
	private static final int DISPLAY_FLAG_TRUSTED = 1 << 7;
	private static final int DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 10;

	@Test
	public void trustedDroppedReturnsTrueWhenRequestedAndMissing() {
		assertTrue(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.FLAG_TRUSTED, 0));
		assertTrue(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.forSdk(33), 0));
		assertTrue(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.forSdk(33), ~DISPLAY_FLAG_TRUSTED));
	}

	@Test
	public void trustedDroppedReturnsFalseWhenRequestedAndPresent() {
		assertFalse(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.FLAG_TRUSTED, DISPLAY_FLAG_TRUSTED));
		assertFalse(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.forSdk(33), DISPLAY_FLAG_TRUSTED));
	}

	@Test
	public void touchFeedbackBitDoesNotMaskADroppedTrusted() {
		assertTrue(VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.forSdk(34), DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED));
	}

	@Test
	public void trustedDroppedReturnsFalseWhenNotRequested() {
		assertFalse(VirtualDisplayFlags.trustedDropped(0, 0));
		assertFalse(VirtualDisplayFlags.trustedDropped(0, DISPLAY_FLAG_TRUSTED));
		int untrusted = VirtualDisplayFlags.forSdk(33) & ~VirtualDisplayFlags.FLAG_TRUSTED;
		assertFalse(VirtualDisplayFlags.trustedDropped(untrusted, 0));
	}

	@Test
	public void trustedDroppedAlwaysFalseBeforeAndroid11BecauseTrustedIsNeverRequested() {
		for (int sdk = 28; sdk <= 29; sdk++) {
			assertFalse("SDK " + sdk, VirtualDisplayFlags.trustedDropped(VirtualDisplayFlags.forSdk(sdk), 0));
		}
	}

	// AOSP Display.java (android14..16-release): public static final int FLAG_OWN_FOCUS = 1 << 11;
	private static final int DISPLAY_FLAG_OWN_FOCUS = 1 << 11;

	@Test
	public void focusDroppedWhenRequestedAndMissing() {
		assertTrue(VirtualDisplayFlags.focusDropped(VirtualDisplayFlags.forSdk(34), 0));
		assertTrue(VirtualDisplayFlags.focusDropped(VirtualDisplayFlags.forVirtualDevice(36), ~DISPLAY_FLAG_OWN_FOCUS));
	}

	@Test
	public void focusNotDroppedWhenPresent() {
		assertFalse(VirtualDisplayFlags.focusDropped(VirtualDisplayFlags.forSdk(34), DISPLAY_FLAG_OWN_FOCUS));
		assertFalse(VirtualDisplayFlags.focusDropped(VirtualDisplayFlags.forVirtualDevice(36), DISPLAY_FLAG_OWN_FOCUS));
	}

	@Test
	public void focusNotDroppedWhenNeverRequested() {
		for (int sdk = 28; sdk < 34; sdk++) {
			assertFalse("SDK " + sdk, VirtualDisplayFlags.focusDropped(VirtualDisplayFlags.forSdk(sdk), 0));
		}
	}
}
