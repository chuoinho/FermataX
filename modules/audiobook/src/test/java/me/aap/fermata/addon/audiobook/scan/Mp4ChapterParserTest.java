package me.aap.fermata.addon.audiobook.scan;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

import me.aap.fermata.addon.audiobook.scan.EmbeddedChapterParser.ChapterMark;

@RunWith(RobolectricTestRunner.class)
public class Mp4ChapterParserTest {
	@Test
	public void parsesChplAtom() throws Exception {
		List<ChapterMark> chapters = parseFixture("chpl.m4a");

		assertEquals(List.of(
				new ChapterMark(0, "Introduction"),
				new ChapterMark(10_000, "Chapter 1"),
				new ChapterMark(20_000, "Chapter 2")), chapters);
	}

	@Test
	public void parsesQuickTimeChapterTrack() throws Exception {
		List<ChapterMark> chapters = parseFixture("chapter_track_id.m4b");

		assertEquals(108, chapters.size());
		assertEquals(new ChapterMark(0, "Opening Credits"), chapters.get(0));
		assertEquals(new ChapterMark(103_121_056, "Closing Credits"), chapters.get(107));
	}

	private static List<ChapterMark> parseFixture(String name) throws Exception {
		File file = File.createTempFile("audiobook-", '-' + name);
		file.deleteOnExit();
		try (InputStream in = Mp4ChapterParserTest.class.getResourceAsStream('/' + name)) {
			if (in == null) throw new IllegalStateException("Missing fixture " + name);
			Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return new Mp4ChapterParser(RuntimeEnvironment.getApplication())
				.parse(Uri.fromFile(file));
	}
}
