package me.app.fermatax.auto.carapps;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads both the table and Android 17 object formats of {@code cmd companiondevice list}. */
public final class ShellAssociation {
	public static final String PACKAGE = "com.android.shell";
	private static final Pattern ASSOCIATION = Pattern.compile(
			"^Association\\{mId=(\\d+), mUserId=0, mPackageName='([^']+)', mDeviceMacAddress=([^,]+),.*");

	private ShellAssociation() {
	}

	@Nullable
	public static Integer findId(String list, String mac) {
		for (String line : list.split("\n")) {
			String[] parts = line.split("\\|");
			Matcher association = ASSOCIATION.matcher(line.trim());
			if (association.matches()) {
				parts = new String[]{association.group(1), association.group(2), association.group(3)};
			}
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
