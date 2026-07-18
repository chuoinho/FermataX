package me.aap.fermata.addon.radio;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.utils.async.Completed.completed;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.generic.GenericFileSystem;

class RadioStationItem extends RadioPlayableItem {
	private static final String ID_PREFIX = RadioRootItem.SCHEME + ":station:";
	private final RadioBrowserStation station;
	private final RadioBrowserApi api;

	RadioStationItem(@NonNull BrowsableItem parent, @NonNull RadioBrowserStation station) {
		super(toId(station), parent, GenericFileSystem.getInstance().create(station.getStreamUrl()));
		this.station = station;
		this.api = ((RadioRootItem) parent.getRoot()).getApi();
	}

	static FutureSupplier<RadioStationItem> create(RadioRootItem root, String id) {
		String uuid = Uri.decode(id.substring(ID_PREFIX.length()));
		return root.getApi().getStation(uuid).map(station ->
				(station == null) ? null : new RadioStationItem(root, station));
	}

	static boolean isStationId(String id) {
		return id.startsWith(ID_PREFIX);
	}

	@NonNull
	@Override
	public String getName() {
		return station.getName();
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
		api.click(station.uuid).onFailure(err -> Log.d(err, "Failed to register radio click: ", station.uuid));
		return Uri.parse(station.getStreamUrl());
	}

	@Nullable
	@Override
	public String getUserAgent() {
		return RadioBrowserApi.USER_AGENT;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MetadataBuilder meta = new MetadataBuilder();
		meta.putString(METADATA_KEY_TITLE, station.getName());
		if (!station.getArtist().isEmpty()) meta.putString(METADATA_KEY_ARTIST, station.getArtist());
		meta.putString(METADATA_KEY_ALBUM, "Internet Radio");
		return buildMeta(meta);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return station.getSubtitle();
	}

	private static String toId(RadioBrowserStation station) {
		return ID_PREFIX + Uri.encode(station.uuid);
	}
}
