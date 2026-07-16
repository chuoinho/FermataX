package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamMovie {
	private final int streamId;
	private final String name;
	private final String icon;
	private final String categoryId;
	private final String containerExtension;

	public XtreamMovie(int streamId, String name, String icon, String categoryId,
										 String containerExtension) {
		this.streamId = streamId;
		this.name = (name == null) || name.isEmpty() ? String.valueOf(streamId) : name;
		this.icon = icon;
		this.categoryId = categoryId;
		this.containerExtension = containerExtension;
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

	public String getCategoryId() {
		return categoryId;
	}

	public String getContainerExtension() {
		return containerExtension;
	}
}
