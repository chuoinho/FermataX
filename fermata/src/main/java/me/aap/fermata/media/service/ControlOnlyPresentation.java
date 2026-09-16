package me.aap.fermata.media.service;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Immutable media-session presentation for a renderer outside the media-engine pipeline. */
public record ControlOnlyPresentation(long leaseId, String addonClass, int state, long actions,
		@Nullable MediaMetadataCompat metadata) {
	public ControlOnlyPresentation {
		if (leaseId <= 0L) throw new IllegalArgumentException("Invalid control-only lease");
		addonClass = Objects.requireNonNull(addonClass, "Missing addon class").trim();
		if (addonClass.isEmpty()) throw new IllegalArgumentException("Missing addon class");
	}
}
