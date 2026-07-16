package me.aap.fermata.addon.audiobook.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookSource;
import me.aap.fermata.addon.audiobook.model.AudiobookSourceType;
import me.aap.fermata.addon.audiobook.security.AudiobookCredential;

@RunWith(RobolectricTestRunner.class)
public class AudiobookshelfClientTest {
	@Test
	public void parsesLoginAndNormalizesEndpoint() throws Exception {
		AudiobookCredential credential = AudiobookshelfClient.parseLogin("""
				{"user":{"username":"reader","accessToken":"access",
				"refreshToken":"refresh"}}
				""", "https://books.example/base", "fallback");

		assertEquals("reader", credential.username());
		assertEquals("Bearer access", credential.authorization());
		assertEquals("https://books.example/base", AudiobookshelfClient.normalizeEndpoint(
				"HTTPS://Books.Example/base///"));
	}

	@Test
	public void parsesExpandedBookTracksChaptersAndProgress() throws Exception {
		AudiobookSource source = new AudiobookSource("abs-source", AudiobookSourceType.AUDIOBOOKSHELF,
				"Audiobookshelf", "https://books.example", "credential", 1, 1);
		AudiobookBook summary = new AudiobookBook("abs-book", source.getId(), "remote-book",
				"Summary", "Author", "", "", "", "en", 120_000, null, 0, 0, false, 1, 1);
		String json = """
				{"updatedAt":99,"media":{
				 "metadata":{"title":"Expanded","authorName":"Author","language":"en"},
				 "duration":120,"tracks":[
				  {"index":1,"startOffset":0,"duration":60,"title":"File 1",
				   "contentUrl":"/api/items/remote-book/file/1","mimeType":"audio/mpeg"},
				  {"index":2,"startOffset":60,"duration":60,"title":"File 2",
				   "contentUrl":"/api/items/remote-book/file/2","mimeType":"audio/mpeg"}],
				 "chapters":[
				  {"start":0,"end":30,"title":"Opening"},
				  {"start":30,"end":60,"title":"Part 1"},
				  {"start":60,"end":120,"title":"Part 2"}]},
				 "userMediaProgress":{"currentTime":45,"isFinished":false,"lastUpdate":88}}
				""";

		AudiobookshelfBookDetails details = AudiobookshelfClient.parseDetails(json, source,
				summary, 100);

		assertEquals("Expanded", details.book().getTitle());
		assertEquals(3, details.chapters().size());
		assertEquals(30_000, details.chapters().get(1).getBookOffsetMs());
		assertEquals(30_000, details.chapters().get(1).getOffsetMs());
		assertEquals(15_000, details.book().getProgressMs());
		assertEquals(details.chapters().get(1).getId(), details.book().getProgressChapterId());
		assertTrue(details.chapters().get(2).getMediaUrl().endsWith("/api/items/remote-book/file/2"));
	}

	@Test
	public void parsesLibrariesAndMinifiedBookPage() throws Exception {
		assertEquals(1, AudiobookshelfClient.parseLibraries("""
				[{"id":"library-1","name":"Books","mediaType":"book"},
				 {"id":"podcasts","name":"Podcasts","mediaType":"podcast"}]
				""").stream().filter(library -> library.mediaType().equals("book")).count());
		AudiobookSource source = new AudiobookSource("source", AudiobookSourceType.AUDIOBOOKSHELF,
				"ABS", "https://books.example", "credential", 1, 1);
		AudiobookshelfClient.BookPage page = AudiobookshelfClient.parseBookPage("""
				{"total":1,"results":[{"id":"book-1","addedAt":10,"media":{
				"duration":12,"metadata":{"title":"Book","authorName":"A"}}}]}
				""", source, 20);
		assertEquals(1, page.books().size());
		assertEquals("book-1", page.books().get(0).getRemoteId());
	}
}
