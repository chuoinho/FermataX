package me.aap.fermata.addon.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChatAddonTest {
	@Test
	public void builtInModelIgnoresStaleCustomText() {
		assertEquals("gpt-5.4-mini", ChatAddon.resolveModel(0, "gpt-custom"));
	}

	@Test
	public void customModelRequiresExplicitCustomSelection() {
		assertEquals("gpt-custom", ChatAddon.resolveModel(5, "gpt-custom"));
	}

	@Test
	public void emptyOrInvalidCustomSelectionFallsBackToDefault() {
		assertEquals("gpt-5.4-mini", ChatAddon.resolveModel(5, " "));
		assertEquals("gpt-5.4-mini", ChatAddon.resolveModel(-1, "gpt-custom"));
	}
}
