package me.aap.fermata.media.lib;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DefaultMediaLibCacheContractTest {

	// A collected item can stay in the map until its reference is dequeued; re-creating it is
	// legitimate and must not fail the whole list ("Unable to load items") in debug builds.
	@Test
	public void duplicateCheckIgnoresCollectedItems() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(
				"src/main/java/me/aap/fermata/media/lib/DefaultMediaLib.java")), StandardCharsets.UTF_8);
		int add = source.indexOf("void addToCache(Item i)");
		String body = source.substring(add, source.indexOf("itemCache.put(", add));
		assertFalse(body.contains("itemCache.containsKey(id)"));
		assertTrue(body.contains("r.get()") && body.contains("existing != null"));
	}
}
