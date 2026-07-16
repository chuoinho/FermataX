package me.aap.fermata.addon.radio;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.generic.GenericFileSystem;

final class RadioSourceItem extends ExtPlayable implements RadioItem {
	private static final String ID_PREFIX = RadioRootItem.SCHEME + ":source:";
	private final RadioSource source;

	RadioSourceItem(@NonNull BrowsableItem parent, @NonNull RadioSource source) {
		super(toId(source), parent, GenericFileSystem.getInstance().create(source.getUrl()));
		this.source = source;
	}

	static boolean isSourceId(String id) {
		return id.startsWith(ID_PREFIX);
	}

	static String sourceId(String id) {
		return Uri.decode(id.substring(ID_PREFIX.length()));
	}

	RadioSource getSource() {
		return source;
	}

	@NonNull
	@Override
	public String getName() {
		return source.getName();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.radio;
	}

	@Override
	public boolean isStream() {
		return true;
	}

	@NonNull
	@Override
	public Uri getLocation() {
		return Uri.parse(source.getUrl());
	}

	@Override
	public String getUserAgent() {
		return RadioBrowserApi.USER_AGENT;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MetadataBuilder meta = new MetadataBuilder();
		meta.putString(METADATA_KEY_TITLE, source.getName());
		meta.putString(METADATA_KEY_ALBUM, "Internet Radio");
		return buildMeta(meta);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return source.getUrl();
	}

	private static String toId(RadioSource source) {
		return ID_PREFIX + Uri.encode(source.getId());
	}
}
