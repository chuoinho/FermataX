package me.aap.fermata.addon.radio;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

final class RadioSourceItem extends RadioPlayableItem {
	private static final String ID_PREFIX = RadioRootItem.SCHEME + ":source:";
	private final RadioSource source;
	private volatile String metadataRevision;

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
		RadioSource current = resolveSource();
		return (current == null) ? source : current;
	}

	@Nullable
	private RadioSource resolveSource() {
		if (!(getRoot() instanceof RadioRootItem root)) return source;
		return root.findSource(source.getId());
	}

	private RadioSource requireSource() {
		RadioSource current = resolveSource();
		if (current == null) throw new IllegalStateException("Radio source has been deleted");
		return current;
	}

	@NonNull
	@Override
	public String getName() {
		return getSource().getName();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.radio;
	}

	@Override
	public boolean isStream() {
		return true;
	}

	@Override
	public boolean isRecentEligible() {
		return resolveSource() != null;
	}

	@NonNull
	@Override
	public Uri getLocation() {
		return Uri.parse(requireSource().getUrl());
	}

	@NonNull
	@Override
	public VirtualResource getResource() {
		return GenericFileSystem.getInstance().create(requireSource().getUrl());
	}

	@Override
	public String getUserAgent() {
		return RadioBrowserApi.USER_AGENT;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		RadioSource current = getSource();
		metadataRevision = revision(current);
		MetadataBuilder meta = new MetadataBuilder();
		meta.putString(METADATA_KEY_TITLE, current.getName());
		meta.putString(METADATA_KEY_ALBUM, "Internet Radio");
		return buildMeta(meta);
	}

	@Override
	protected boolean isMediaDataValid(FutureSupplier<MediaMetadataCompat> data) {
		return (data != null) && Objects.equals(metadataRevision, revision(resolveSource()));
	}

	@Override
	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> description) {
		return (description != null) && Objects.equals(metadataRevision, revision(resolveSource()));
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return getSource().getUrl();
	}

	private static String toId(RadioSource source) {
		return ID_PREFIX + Uri.encode(source.getId());
	}

	@Nullable
	private static String revision(@Nullable RadioSource source) {
		return (source == null) ? null : source.getName() + '\0' + source.getUrl();
	}
}
