package me.aap.fermata.addon.tv.xtream;

/**
 * @author Andrey Pavlenko
 */
public class XtreamCategory {
	private final String id;
	private final String name;
	private final String parentId;

	public XtreamCategory(String id, String name, String parentId) {
		this.id = id;
		this.name = (name == null) || name.isEmpty() ? id : name;
		this.parentId = parentId;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getParentId() {
		return parentId;
	}
}
