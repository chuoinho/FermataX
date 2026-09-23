package me.aap.fermata.addon.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.web.audio.WebAudioProfile;

public class GenericWebAudioBridgeTest {
	@Test
	public void scriptHasDeterministicSafetyCheckAndAudioProcessingGraph() {
		String source = GenericWebAudioBridge.shimSource(15L, WebAudioProfile.unity());
		assertTrue(source.contains("window.top !== window"));
		assertTrue(source.contains("isSafe"));
		assertTrue(source.contains("blob:"));
		assertTrue(source.contains("data:"));
		assertTrue(source.contains("crossOrigin"));
		assertTrue(source.contains("lowshelf"));
		assertTrue(source.contains("highshelf"));
		assertTrue(source.contains("createDynamicsCompressor()"));
		assertTrue(source.contains("dinhDb"));
		assertTrue(source.contains("window.__fermataGenericWebAudioV1"));
		assertEquals(1, occurrences(source, "createMediaElementSource("));
	}

	@Test
	public void profileUpdatesAndTeardownAreGenerationBound() {
		String updateSource = GenericWebAudioBridge.updateProfileSource(15L, WebAudioProfile.unity());
		assertTrue(updateSource.contains("updateProfile(15,"));
		String teardownSource = GenericWebAudioBridge.teardownSource(15L);
		assertTrue(teardownSource.contains("teardown(15)"));

		String shim = GenericWebAudioBridge.shimSource(15L, WebAudioProfile.unity());
		assertTrue(shim.contains("if (disposed) return;"));
		assertTrue(shim.contains("document.removeEventListener"));
		assertTrue(shim.contains("active.context.close()"));
		assertTrue(shim.contains("if (profile.m && profile.e && !active)"));
		assertTrue(shim.contains("document.querySelectorAll('video, audio')"));
	}

	private static int occurrences(String value, String needle) {
		int count = 0, index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
