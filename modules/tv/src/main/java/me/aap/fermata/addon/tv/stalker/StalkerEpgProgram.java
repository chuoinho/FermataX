package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerEpgProgram(String id, String channelId, long startTime, long endTime,
		String title, @Nullable String description, @Nullable String icon,
		boolean archive) {
	public StalkerEpgProgram {
		if ((title == null) || title.isBlank()) title = "EPG";
	}

	public boolean isValid() {
		return (id != null) && !id.isBlank() && (startTime > 0) && (endTime > startTime);
	}
}
