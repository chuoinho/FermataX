package me.aap.fermata.ui.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class VoiceSessionTest {
	@Test
	public void selectionIsBoundedAndUsesStableIds() {
		VoiceSession s = new VoiceSession();
		s.beginSelection(List.of(
				new VoiceSession.Option("video-a", "Numb", "Linkin Park"),
				new VoiceSession.Option("video-b", "Numb", "Live"),
				new VoiceSession.Option("video-c", "Numb", "Cover"),
				new VoiceSession.Option("video-d", "Numb", "Other")), 100);
		assertEquals(3, s.getOptions().size());
		assertEquals("video-b", s.resolveSelection("number two", Locale.ENGLISH, 101).getStableId());
		assertEquals(VoiceSession.Mode.COMMAND, s.getMode());
	}

	@Test
	public void expiredOrInvalidSelectionNeverAutoplays() {
		VoiceSession s = new VoiceSession();
		s.beginSelection(List.of(new VoiceSession.Option("a", "A", null)), 100);
		assertNull(s.resolveSelection("number two", Locale.ENGLISH, 101));
		assertEquals(VoiceSession.Mode.SELECTION, s.getMode());

		s.beginSelection(List.of(new VoiceSession.Option("a", "A", null)), 100);
		assertNull(s.resolveSelection("number one", Locale.ENGLISH,
				100 + VoiceSession.SELECTION_TIMEOUT_MS));
		assertEquals(VoiceSession.Mode.COMMAND, s.getMode());
	}

	@Test
	public void addonOwnedOptionCarriesStableTarget() {
		VoiceSession.Option option = new VoiceSession.Option("youtube:video-id", "Numb",
				"Linkin Park", "youtube");
		assertEquals("youtube", option.getVoiceTarget());
	}

	@Test
	public void navigationClearInvalidatesPendingSelection() {
		VoiceSession s = new VoiceSession();
		s.beginSelection(List.of(
				new VoiceSession.Option("video-a", "First", null),
				new VoiceSession.Option("video-b", "Second", null)), 100);

		s.clear();

		assertNull(s.resolveSelection("two", Locale.ENGLISH, 101));
		assertEquals(VoiceSession.Mode.COMMAND, s.getMode());
	}
}
