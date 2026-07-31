package me.aap.fermata.addon.stremio.item;

import static me.aap.utils.async.Completed.completed;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;
import me.aap.fermata.addon.stremio.security.StremioArtworkLoader;
import me.aap.fermata.media.lib.ExtBrowsable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;

abstract class StremioBrowsableItem extends ExtBrowsable {
	private final BrowsableItem root;
	private final StremioItemGateway gateway;
	private final String title;
	private final String subtitle;
	private final String description;
	private final String artwork;

	StremioBrowsableItem(String id, BrowsableItem parent, BrowsableItem root,
			StremioItemGateway gateway, String title, @Nullable String subtitle,
			@Nullable String description, @Nullable String artwork) {
		super(id, parent, null);
		this.root = java.util.Objects.requireNonNull(root, "root");
		if (!StremioRootItem.ID.equals(root.getId())) {
			throw new IllegalArgumentException("Stremio item requires the Stremio root");
		}
		this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
		this.title = requireTitle(title);
		this.subtitle = emptyIfNull(subtitle);
		this.description = emptyIfNull(description);
		this.artwork = ArtworkUrlSanitizer.sanitize(artwork);
	}

	final StremioItemGateway gateway() {
		return gateway;
	}

	static String text(BrowsableItem root, int resourceId, String fallback) {
		try {
			var lib = root.getLib();
			return (lib == null) ? fallback : lib.getContext().getString(resourceId);
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	@NonNull
	@Override
	public final String getName() {
		return title;
	}

	@NonNull
	@Override
	public final BrowsableItem getRoot() {
		return root;
	}

	@NonNull
	@Override
	protected final FutureSupplier<String> buildTitle() {
		return completed(title);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed(subtitle);
	}

	@Override
	protected FutureSupplier<String> buildDescription() {
		return completed(description);
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return StremioArtworkLoader.load(artwork);
	}

	@Override
	public final boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public final boolean getTitleSeqNumPref() {
		return false;
	}

	private static String requireTitle(String value) {
		java.util.Objects.requireNonNull(value, "title");
		if (value.isBlank()) throw new IllegalArgumentException("title cannot be blank");
		return value;
	}

	private static String emptyIfNull(String value) {
		return (value == null) ? "" : value;
	}
}
