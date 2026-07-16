package me.aap.fermata.addon.podcast.feed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.podcast.model.PodcastOpmlEntry;

public class PodcastOpmlCodecTest {
	private final PodcastOpmlCodec codec = new PodcastOpmlCodec();

	@Test
	public void flattensNestedOutlinesSkipsInvalidAndDeduplicates() throws Exception {
		List<PodcastOpmlEntry> entries = codec.parse(stream("""
				<opml version="2.0"><body><outline text="Folder">
				 <outline text="One" xmlUrl="https://example.test/one.xml"/>
				 <outline title="Duplicate" xmlUrl="https://example.test/one.xml"/>
				 <outline text="Invalid" xmlUrl="file:///private.xml"/>
				 <outline title="Two" xmlUrl="http://example.test/two.xml"/>
				</outline></body></opml>
				"""));

		assertEquals(2, entries.size());
		assertEquals("One", entries.get(0).title());
		assertEquals("Two", entries.get(1).title());
	}

	@Test
	public void rejectsDoctypeAndMalformedDocument() {
		assertThrows(IOException.class, () -> codec.parse(stream("""
				<!DOCTYPE opml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<opml><body><outline xmlUrl="https://example.test/&xxe;"/></body></opml>
				""")));
		assertThrows(IOException.class, () -> codec.parse(stream("<html/>")));
	}

	@Test
	public void exportOmitsPrivateByDefaultAndNeverWritesBasicPassword() throws Exception {
		List<PodcastOpmlEntry> entries = List.of(
				new PodcastOpmlEntry("Public & Good", "https://example.test/public.xml"),
				new PodcastOpmlEntry("Token", "https://example.test/private?token=secret"),
				new PodcastOpmlEntry("Basic", "https://driver:password@example.test/basic.xml"));
		ByteArrayOutputStream safe = new ByteArrayOutputStream();
		codec.write(entries, safe, false);
		String safeXml = safe.toString(UTF_8);
		assertTrue(safeXml.contains("Public &amp; Good"));
		assertFalse(safeXml.contains("secret"));
		assertFalse(safeXml.contains("password"));

		ByteArrayOutputStream approved = new ByteArrayOutputStream();
		codec.write(entries, approved, true);
		String approvedXml = approved.toString(UTF_8);
		assertTrue(approvedXml.contains("token=secret"));
		assertFalse(approvedXml.contains("password"));
	}

	private static ByteArrayInputStream stream(String value) {
		return new ByteArrayInputStream(value.getBytes(UTF_8));
	}
}
