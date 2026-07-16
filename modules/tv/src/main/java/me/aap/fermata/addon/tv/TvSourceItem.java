package me.aap.fermata.addon.tv;

import me.aap.fermata.media.lib.MediaLib;

/**
 * @author Andrey Pavlenko
 */
public interface TvSourceItem extends TvItem, MediaLib.BrowsableItem {
	String TYPE_M3U = "m3u";
	String TYPE_XTREAM = "xtream";

	int getSourceId();

	String getSourceType();
}
