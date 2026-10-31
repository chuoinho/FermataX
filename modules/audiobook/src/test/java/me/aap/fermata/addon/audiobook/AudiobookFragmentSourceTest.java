package me.aap.fermata.addon.audiobook;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AudiobookFragmentSourceTest {

	// The source dialogs only report OK; like PodcastFragment, the caller must return from the
	// dialog, otherwise it stays open and the refreshed list is skipped (no view).
	@Test
	public void sourceDialogsReturnToTheLibraryOnOk() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get(
				"src/main/java/me/aap/fermata/addon/audiobook/AudiobookFragment.java")),
				StandardCharsets.UTF_8);
		for (String add : new String[]{"getRootItem().addAudiobookshelf(", "getRootItem().addOpds("}) {
			int call = source.indexOf(add);
			int ok = source.lastIndexOf("input.onSuccess(", call);
			int back = source.indexOf("getMainActivity().showFragment(getFragmentId());", ok);
			assertTrue(add, (ok >= 0) && (back > ok) && (back < call));
		}
	}
}
