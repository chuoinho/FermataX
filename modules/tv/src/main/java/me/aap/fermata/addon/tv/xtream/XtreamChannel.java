package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamChannel {
	private final int streamId;
	private final String name;
	private final String icon;
	private final String epgChannelId;
	private final boolean tvArchive;
	private final int tvArchiveDuration;
	private final String categoryId;

	public XtreamChannel(int streamId, String name, String icon, String epgChannelId,
											 boolean tvArchive, int tvArchiveDuration, String categoryId) {
		this.streamId = streamId;
		this.name = (name == null) || name.isEmpty() ? String.valueOf(streamId) : name;
		this.icon = icon;
		this.epgChannelId = epgChannelId;
		this.tvArchive = tvArchive;
		this.tvArchiveDuration = tvArchiveDuration;
		this.categoryId = categoryId;
	}

	public int getStreamId() {
		return streamId;
	}

	public String getName() {
		return name;
	}

	public String getIcon() {
		return icon;
	}

	public String getEpgChannelId() {
		return epgChannelId;
	}

	public boolean isTvArchive() {
		return tvArchive;
	}

	public int getTvArchiveDuration() {
		return tvArchiveDuration;
	}

	public String getCategoryId() {
		return categoryId;
	}
}
