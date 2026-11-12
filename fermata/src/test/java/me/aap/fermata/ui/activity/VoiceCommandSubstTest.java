package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Map;

public class VoiceCommandSubstTest {

	@Test
	public void wordsAfterAnEmojiAreKept() {
		assertEquals("play 🎵 music", VoiceCommandHandler.subst("Play 🎵 Music", Map.of()));
	}

	@Test
	public void substitutesWholeWords() {
		assertEquals("play next song",
				VoiceCommandHandler.subst("play  forward song", Map.of("forward", "next")));
	}
}
