package me.app.fermatax.auto.carapps;

import java.util.Objects;

/**
 * Navigation state model for the Car Apps screen and bottom bar.
 */
public final class CarAppsNavModel {

	public enum Screen {
		DRAWER,
		APP
	}

	public final boolean backEnabled;
	public final boolean drawerHighlighted;
	public final boolean closeEnabled;

	public CarAppsNavModel(boolean backEnabled, boolean drawerHighlighted, boolean closeEnabled) {
		this.backEnabled = backEnabled;
		this.drawerHighlighted = drawerHighlighted;
		this.closeEnabled = closeEnabled;
	}

	public static CarAppsNavModel of(Screen s) {
		if (s == Screen.DRAWER) {
			return new CarAppsNavModel(false, true, false);
		} else {
			return new CarAppsNavModel(true, false, true);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CarAppsNavModel that = (CarAppsNavModel) o;
		return backEnabled == that.backEnabled &&
				drawerHighlighted == that.drawerHighlighted &&
				closeEnabled == that.closeEnabled;
	}

	@Override
	public int hashCode() {
		return Objects.hash(backEnabled, drawerHighlighted, closeEnabled);
	}

	@Override
	public String toString() {
		return "CarAppsNavModel{" +
				"backEnabled=" + backEnabled +
				", drawerHighlighted=" + drawerHighlighted +
				", closeEnabled=" + closeEnabled +
				'}';
	}
}
