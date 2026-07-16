package me.aap.fermata.addon.audiobook.catalog;

public record LibriVoxBook(String identifier, String title, String author,
		String description, String language, long downloads) {
	public String artworkUrl() {
		return "https://archive.org/services/img/" + identifier;
	}
}
