package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamSeries {
	private final int seriesId;
	private final String name;
	private final String icon;
	private final String categoryId;

	public XtreamSeries(int seriesId, String name, String icon, String categoryId) {
		this.seriesId = seriesId;
		this.name = (name == null) || name.isEmpty() ? String.valueOf(seriesId) : name;
		this.icon = icon;
		this.categoryId = categoryId;
	}

	public int getSeriesId() {
		return seriesId;
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
}
