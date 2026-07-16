package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamEpisode {
	private final int episodeId;
	private final int seasonNumber;
	private final int episodeNumber;
	private final String name;
	private final String icon;
	private final String containerExtension;

	public XtreamEpisode(int episodeId, int seasonNumber, int episodeNumber, String name,
											 String icon, String containerExtension) {
		this.episodeId = episodeId;
		this.seasonNumber = seasonNumber;
		this.episodeNumber = episodeNumber;
		this.name = (name == null) || name.isEmpty() ? fallbackName(episodeId, episodeNumber) : name;
		this.icon = icon;
		this.containerExtension = containerExtension;
	}

	public int getEpisodeId() {
		return episodeId;
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public int getEpisodeNumber() {
		return episodeNumber;
	}

	public String getName() {
		return name;
	}

	public String getIcon() {
		return icon;
	}

	public String getContainerExtension() {
		return containerExtension;
	}

	private static String fallbackName(int episodeId, int episodeNumber) {
		if (episodeNumber > 0) return "Episode " + episodeNumber;
		return String.valueOf(episodeId);
	}
}
