package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class VirtualDisplayFlagsTest {

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
		for (int sdk = 33; sdk <= 37; sdk++) {
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
	public void droppingTrustedKeepsEveryOtherFlag() {
		assertEquals(0x794B, VirtualDisplayFlags.forSdk(34) & ~VirtualDisplayFlags.FLAG_TRUSTED);
		assertEquals(0x394B, VirtualDisplayFlags.forSdk(33) & ~VirtualDisplayFlags.FLAG_TRUSTED);
	}
}
