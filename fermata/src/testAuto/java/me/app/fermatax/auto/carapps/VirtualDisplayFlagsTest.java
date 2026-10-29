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
}
