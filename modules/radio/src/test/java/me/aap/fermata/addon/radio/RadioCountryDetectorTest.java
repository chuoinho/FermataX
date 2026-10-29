package me.aap.fermata.addon.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RadioCountryDetectorTest {

	@Test
	public void prefersNetworkCountryIso() {
		assertEquals("VN", RadioCountryDetector.resolveCountryCode("vn", "us", "de"));
	}

	@Test
	public void fallsBackToSimWhenNetworkEmptyOrNull() {
		assertEquals("US", RadioCountryDetector.resolveCountryCode("", "us", "de"));
		assertEquals("US", RadioCountryDetector.resolveCountryCode("   ", "US", "DE"));
		assertEquals("US", RadioCountryDetector.resolveCountryCode(null, "us", "de"));
	}

	@Test
	public void fallsBackToLocaleWhenNetworkAndSimEmpty() {
		assertEquals("DE", RadioCountryDetector.resolveCountryCode(null, null, "de"));
		assertEquals("DE", RadioCountryDetector.resolveCountryCode("", "  ", "de"));
	}

	@Test
	public void returnsNullWhenAllEmptyOrWhitespace() {
		assertNull(RadioCountryDetector.resolveCountryCode(null, null, null));
		assertNull(RadioCountryDetector.resolveCountryCode("", "", ""));
		assertNull(RadioCountryDetector.resolveCountryCode("   ", " ", "    "));
	}

	@Test
	public void trimsAndNormalizesToUpperCase() {
		assertEquals("FR", RadioCountryDetector.resolveCountryCode("  fr  ", null, null));
	}
}
