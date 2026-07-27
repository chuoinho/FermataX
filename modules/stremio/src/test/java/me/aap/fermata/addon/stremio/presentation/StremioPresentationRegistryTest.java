package me.aap.fermata.addon.stremio.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StremioPresentationRegistryTest {
	@Test
	public void evictsByAccessOrderAndKeepsTypesAtTheBoundary() {
		StremioPresentationRegistry<String> registry = new StremioPresentationRegistry<>(2);
		registry.put("first", "one");
		registry.put("second", "two");
		assertEquals("one", registry.get("first"));

		registry.put("third", "three");

		assertNull(registry.get("second"));
		assertEquals("one", registry.get("first"));
		assertEquals("three", registry.get("third"));
	}

	@Test
	public void putIfAbsentPreservesTheOriginalTarget() {
		StremioPresentationRegistry<String> registry = new StremioPresentationRegistry<>(2);
		registry.putIfAbsent("item", "original");
		registry.putIfAbsent("item", "replacement");
		assertEquals("original", registry.get("item"));
		registry.clear();
		assertNull(registry.get("item"));
	}
}
