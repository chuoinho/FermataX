package me.aap.fermata.addon.audiobook;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AudiobookRootItemSourceTest {

	// Android Auto restores the last chapter on start; listing its book again must reuse that
	// item, otherwise the duplicate id fails the whole chapter list ("Unable to load items").
	@Test
	public void chapterListReusesCachedChapterItem() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(
				"src/main/java/me/aap/fermata/addon/audiobook/AudiobookRootItem.java")),
				StandardCharsets.UTF_8);
		int create = source.indexOf("private FutureSupplier<AudiobookChapterItem> createChapter(");
		int cached = source.indexOf("getFromCache(", create);
		int created = source.indexOf("new AudiobookChapterItem(", create);
		assertTrue((create >= 0) && (cached > create) && (cached < created));
	}
}
