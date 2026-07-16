package me.aap.fermata.addon.tv.xtream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSeason {
	private final int seasonNumber;
	private final String name;
	private final String icon;
	private final List<XtreamEpisode> episodes;

	public XtreamSeason(int seasonNumber, String name, String icon, List<XtreamEpisode> episodes) {
		this.seasonNumber = seasonNumber;
		this.name = (name == null) || name.isEmpty() ? fallbackName(seasonNumber) : name;
		this.icon = icon;
		this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public String getName() {
		return name;
	}

	public String getIcon() {
		return icon;
	}

	public List<XtreamEpisode> getEpisodes() {
		return episodes;
	}

	private static String fallbackName(int seasonNumber) {
		return (seasonNumber > 0) ? "Season " + seasonNumber : "Season";
	}
}
