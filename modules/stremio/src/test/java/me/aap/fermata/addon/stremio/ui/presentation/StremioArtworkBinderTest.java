package me.aap.fermata.addon.stremio.ui.presentation;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class StremioArtworkBinderTest {
	@Test
	public void candidatesPreferPosterThenBackdropAndRemoveDuplicates() {
		assertEquals(List.of(
				"https://images.example/poster.jpg",
				"https://images.example/background.jpg"),
				StremioArtworkBinder.artworkCandidates(
						"https://images.example/poster.jpg",
						"https://images.example/background.jpg"));
		assertEquals(List.of("https://images.example/poster.jpg"),
				StremioArtworkBinder.artworkCandidates(
						"https://images.example/poster.jpg",
						"https://images.example/poster.jpg"));
	}

	@Test
	public void unsafePrimaryDoesNotBlockSafeFallback() {
		assertEquals(List.of("https://images.example/background.jpg"),
				StremioArtworkBinder.artworkCandidates(
						"http://private.invalid/poster.jpg",
						"https://images.example/background.jpg"));
	}
}
