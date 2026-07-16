package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Locale;

import org.junit.Test;

public class VoiceIntentParserTest {
	@Test
	public void parsesEnglishAddonSearchWithoutAnLlm() {
		VoiceIntent i = VoiceIntentParser.parse("Open YouTube video Numb", Locale.ENGLISH);
		assertEquals(VoiceIntent.Kind.ADDON_SEARCH, i.getKind());
		assertEquals("youtube", i.getAddon());
		assertEquals("numb", i.getQuery());
		assertEquals(VoiceIntent.SearchAction.OPEN, i.getSearchAction());
	}

	@Test
	public void parsesVietnameseMixedCommand() {
		VoiceIntent i = VoiceIntentParser.parse("Mở YouTube video Numb", new Locale("vi"));
		assertEquals("youtube", i.getAddon());
		assertEquals("numb", i.getQuery());
		assertEquals(VoiceIntent.SearchAction.OPEN, i.getSearchAction());
	}

	@Test
	public void rejectsUnknownOrEmptyCommands() {
		assertNull(VoiceIntentParser.parse(null, Locale.ENGLISH));
		assertNull(VoiceIntentParser.parse("weather tomorrow", Locale.ENGLISH));
	}

	@Test
	public void parsesSelectionNumbersOneToThree() {
		assertEquals(0, VoiceIntentParser.parseSelectionIndex("number one"));
		assertEquals(1, VoiceIntentParser.parseSelectionIndex("Số hai"));
		assertEquals(2, VoiceIntentParser.parseSelectionIndex("3"));
	}

	@Test
	public void parsesChatGptAsAnIndependentAddonTarget() {
		VoiceIntent i = VoiceIntentParser.parse("Open ChatGPT what is the weather", Locale.ENGLISH);
		assertEquals(VoiceIntent.Kind.ADDON_SEARCH, i.getKind());
		assertEquals("chatgpt", i.getAddon());
		assertEquals("what is the weather", i.getQuery());
	}

	@Test
	public void convertsMediaSearchToTheSameAddonGrammar() {
		String command = VoiceIntentParser.mediaSearchCommand(
				"YouTube video lofi music", Locale.ENGLISH);
		assertEquals("play YouTube video lofi music", command);
		VoiceIntent intent = VoiceIntentParser.parse(command, Locale.ENGLISH);
		assertEquals("youtube", intent.getAddon());
		assertEquals("lofi music", intent.getQuery());
	}

	@Test
	public void rejectsEmptyMediaSearch() {
		assertNull(VoiceIntentParser.mediaSearchCommand("  ", Locale.ENGLISH));
	}
}
