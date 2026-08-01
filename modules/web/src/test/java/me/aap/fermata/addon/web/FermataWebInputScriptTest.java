package me.aap.fermata.addon.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FermataWebInputScriptTest {
	@Test
	public void projectedInputProbeSupportsModernEditableElements() {
		String script = FermataWebView.textInputProbeScript();

		assertTrue(script.contains("tag === 'textarea'"));
		assertTrue(script.contains("role === 'textbox'"));
		assertTrue(script.contains("role === 'searchbox'"));
		assertTrue(script.contains("e.shadowRoot"));
		assertTrue(script.contains("e.contentDocument"));
		assertTrue(script.contains("e.isContentEditable"));
		assertTrue(script.contains("window.__fermataTextInputTarget = e"));
		assertFalse(script.contains("setTimeout(checkInput, 500)"));
	}

	@Test
	public void projectedInputProbeRejectsNonTextInputTypes() {
		String script = FermataWebView.textInputProbeScript();

		assertTrue(script.contains("checkbox"));
		assertTrue(script.contains("file"));
		assertTrue(script.contains("hidden"));
		assertTrue(script.contains("submit"));
	}

	@Test
	public void projectedInputUpdatesOnlyTheCapturedEditableTarget() {
		String update = FermataWebView.textInputUpdateScript("query");
		String submit = FermataWebView.textInputSubmitScript();

		assertTrue(update.contains("fermataTextInputTarget()"));
		assertTrue(update.contains("e.isConnected !== false"));
		assertFalse(update.contains("else e.textContent=text"));
		assertTrue(submit.contains("fermataTextInputTarget()"));
		assertTrue(submit.contains("window.__fermataTextInputTarget=null"));
	}
}
