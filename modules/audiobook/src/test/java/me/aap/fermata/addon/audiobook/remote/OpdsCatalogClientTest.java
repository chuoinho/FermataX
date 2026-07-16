package me.aap.fermata.addon.audiobook.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.security.AudiobookCredential;

@RunWith(RobolectricTestRunner.class)
public class OpdsCatalogClientTest {
	@Test
	public void parsesOpdsOneAtomAcquisition() throws Exception {
		AudiobookSource source = source();
		OpdsCatalogSnapshot snapshot = OpdsCatalogClient.parseFixture("""
				<?xml version="1.0" encoding="utf-8"?>
				<feed xmlns="http://www.w3.org/2005/Atom">
				 <entry><title>Road Book</title><id>urn:book:1</id>
				  <author><name>Reader</name></author>
				  <link rel="http://opds-spec.org/acquisition/open-access"
				   type="audio/mpeg" href="media/book.mp3" />
				 </entry>
				</feed>
				""", source.getEndpoint(), source);

		assertEquals(1, snapshot.entries().size());
		assertEquals("Road Book", snapshot.entries().get(0).book().getTitle());
		assertEquals("Reader", snapshot.entries().get(0).book().getAuthor());
		assertEquals("https://books.example/opds/media/book.mp3",
				snapshot.entries().get(0).chapters().get(0).getMediaUrl());
	}

	@Test
	public void parsesOpdsTwoJsonAndPreservesQueryEndpoint() throws Exception {
		AudiobookSource source = source();
		OpdsCatalogSnapshot snapshot = OpdsCatalogClient.parseFixture("""
				{"metadata":{"title":"Catalog"},"publications":[{
				 "metadata":{"identifier":"book-2","title":"JSON Book",
				 "author":[{"name":"Author"}],"language":"en"},
				 "links":[{"rel":"http://opds-spec.org/acquisition/open-access",
				 "type":"audio/mp4","href":"/audio/book.m4b"}]}]}
				""", source.getEndpoint(), source);

		assertEquals(1, snapshot.entries().size());
		assertEquals("Author", snapshot.entries().get(0).book().getAuthor());
		assertEquals("https://books.example/audio/book.m4b",
				snapshot.entries().get(0).chapters().get(0).getMediaUrl());
		assertEquals("https://books.example/opds?q=audio",
				OpdsCatalogClient.normalizeEndpoint("HTTPS://Books.Example/opds?q=audio"));
	}

	@Test
	public void createsBasicAndBearerAuthorization() {
		AudiobookCredential basic = new AudiobookCredential("https://books.example", "reader", "",
				null, "secret");
		AudiobookCredential bearer = new AudiobookCredential("https://books.example", "", "token",
				null);

		assertTrue(basic.authorization().startsWith("Basic "));
		assertEquals("Bearer token", bearer.authorization());
	}

	private static AudiobookSource source() {
		return new AudiobookSource("opds-source", AudiobookSourceType.OPDS, "OPDS",
				"https://books.example/opds/", null, 1, 1);
	}
}
