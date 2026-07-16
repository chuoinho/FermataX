package me.aap.fermata.addon.audiobook.model;

public enum AudiobookSourceType {
	LOCAL(1),
	LIBRIVOX(2),
	AUDIOBOOKSHELF(3),
	OPDS(4);

	private final int id;

	AudiobookSourceType(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}

	public static AudiobookSourceType fromId(int id) {
		for (AudiobookSourceType type : values()) if (type.id == id) return type;
		throw new IllegalArgumentException("Unknown audiobook source type: " + id);
	}
}
