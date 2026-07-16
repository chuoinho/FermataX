package me.aap.fermata.addon.audiobook.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookChapter;

@RunWith(RobolectricTestRunner.class)
public class LibriVoxCatalogClientTest {
	@Test
	public void parsesSearchResultsAndArrayFields() throws Exception {
		String json = """
				{"response":{"docs":[{
				 "identifier":"alice_librivox","title":"Alice",
				 "creator":["Lewis Carroll","Reader"],
				 "description":"<p>A free book</p>","language":["eng"],"downloads":123
				}]}}
				""";

		List<LibriVoxBook> books = LibriVoxCatalogClient.parseSearch(input(json));

		assertEquals(1, books.size());
		assertEquals("alice_librivox", books.get(0).identifier());
		assertEquals("Lewis Carroll", books.get(0).author());
		assertEquals("A free book", books.get(0).description());
		assertEquals(123, books.get(0).downloads());
	}

	@Test
	public void metadataPrefersBandwidthFriendlyChapterFiles() throws Exception {
		String json = """
				{
				 "metadata":{"title":"Alice","creator":"Lewis Carroll",
				   "description":"<b>Classic</b>","language":"eng"},
				 "files":[
				  {"name":"Alice.m4b","format":"Audiobook"},
				  {"name":"alice_01_128kb.mp3","title":"01 - Down the Rabbit Hole",
				   "format":"128Kbps MP3","length":"10.5"},
				  {"name":"alice_01_64kb.mp3","title":"01 - Down the Rabbit Hole",
				   "format":"64Kbps MP3","length":"10.5"},
				  {"name":"alice_02_128kb.mp3","title":"02 - The Pool of Tears",
				   "format":"128Kbps MP3","length":"20"},
				  {"name":"alice_02_64kb.mp3","title":"02 - The Pool of Tears",
				   "format":"64Kbps MP3","length":"20"}
				 ]
				}
				""";

		LibriVoxImport imported = LibriVoxCatalogClient.parseMetadata(input(json),
				"alice_librivox", 42);

		assertEquals("Alice", imported.book().getTitle());
		assertEquals("Classic", imported.book().getDescription());
		assertEquals(30_500, imported.book().getDurationMs());
		assertEquals(2, imported.chapters().size());
		AudiobookChapter first = imported.chapters().get(0);
		assertTrue(first.getMediaUrl().endsWith("alice_01_64kb.mp3"));
		assertFalse(first.isSegment());
		assertEquals(10_500, first.getDurationMs());
	}

	@Test
	public void searchUrlStaysInsideLibriVoxCollection() {
		String url = LibriVoxCatalogClient.buildSearchUrl("Alice (Wonderland)",
				LibriVoxCatalogClient.Sort.RELEVANCE, 500);

		assertTrue(url.contains("collection%3Alibrivoxaudio"));
		assertTrue(url.contains("rows=50"));
		assertTrue(url.contains("Alice"));
	}

	private static ByteArrayInputStream input(String value) {
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
	}
}
