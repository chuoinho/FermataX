package me.app.fermatax.auto.carapps;

import androidx.annotation.Nullable;

/** Reads the output of {@code cmd companiondevice list}: one "id | package | mac" line per association. */
public final class ShellAssociation {
	public static final String PACKAGE = "com.android.shell";

	private ShellAssociation() {
	}

	@Nullable
	public static Integer findId(String list, String mac) {
		for (String line : list.split("\n")) {
			String[] parts = line.split("\\|");
			if ((parts.length == 3) && PACKAGE.equals(parts[1].trim()) && mac.equalsIgnoreCase(parts[2].trim())) {
				try {
					return Integer.valueOf(parts[0].trim());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return null;
	}
}
