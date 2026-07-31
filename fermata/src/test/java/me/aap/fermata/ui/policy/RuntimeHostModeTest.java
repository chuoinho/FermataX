package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuntimeHostModeTest {
	@Test
	public void mobileBuildAlwaysUsesPhonePresentation() {
		assertEquals(RuntimeHostMode.PHONE, RuntimeHostMode.resolve(false, false, false));
		assertEquals(RuntimeHostMode.PHONE, RuntimeHostMode.resolve(false, true, true));
	}

	@Test
	public void autoBuildDistinguishesPhoneProjectionAndMirror() {
		assertEquals(RuntimeHostMode.PHONE, RuntimeHostMode.resolve(true, false, false));
		assertEquals(RuntimeHostMode.AA_PROJECTION, RuntimeHostMode.resolve(true, true, false));
		assertEquals(RuntimeHostMode.MIRROR, RuntimeHostMode.resolve(true, false, true));
		assertEquals(RuntimeHostMode.AA_PROJECTION, RuntimeHostMode.resolve(true, true, true));
	}

	@Test
	public void onlyProjectionAndMirrorUseAutomotivePresentation() {
		assertFalse(RuntimeHostMode.PHONE.usesAutomotivePresentation());
		assertTrue(RuntimeHostMode.AA_PROJECTION.usesAutomotivePresentation());
		assertTrue(RuntimeHostMode.MIRROR.usesAutomotivePresentation());
		assertTrue(RuntimeHostMode.AA_PROJECTION.isProjection());
		assertTrue(RuntimeHostMode.MIRROR.isMirror());
	}
}
