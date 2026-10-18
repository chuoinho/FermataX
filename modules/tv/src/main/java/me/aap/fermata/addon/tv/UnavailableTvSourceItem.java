package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * Represents an IPTV source that failed to load (offline, invalid credentials, unreachable host).
 * Retained in the list so that the user can edit or delete it.
 */
public class UnavailableTvSourceItem extends BrowsableItemBase implements TvSourceItem {
	public static final String SCHEME = "tvu";

	private final int sourceId;
	private final String sourceType;
	private final String name;
	private final Throwable failure;

	public UnavailableTvSourceItem(@Nullable BrowsableItem parent, int sourceId,
			@NonNull String sourceType, @NonNull String name, @Nullable Throwable failure) {
		super(toId(sourceId), parent, null);
		this.sourceId = sourceId;
		this.sourceType = sourceType;
		this.name = name;
		this.failure = failure;
	}

	public static String toId(int sourceId) {
		return SharedTextBuilder.get().append(SCHEME).append(':').append(sourceId).releaseString();
	}

	@Override
	public int getSourceId() {
		return sourceId;
	}

	@NonNull
	@Override
	public String getSourceType() {
		return sourceType;
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@Nullable
	public Throwable getFailure() {
		return failure;
	}

	@Override
	public boolean isCacheable() {
		return false;
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		BrowsableItem parent = getParent();
		return (parent != null) ? parent.getRoot() : this;
	}

	@Nullable
	@Override
	public MediaLib getLib() {
		BrowsableItem parent = getParent();
		if (parent != null) {
			BrowsableItem root = parent.getRoot();
			if (root != null) return root.getLib();
		}
		return null;
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completedEmptyList();
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed(name);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		try {
			MediaLib lib = getLib();
			if (lib != null) {
				Context ctx = lib.getContext();
				if (ctx != null) {
					return completed(ctx.getString(R.string.tv_source_unavailable));
				}
			}
		} catch (Throwable ignored) {
		}
		return completed("Unavailable");
	}

	@Override
	public FutureSupplier<Void> refresh() {
		BrowsableItem parent = getParent();
		if (parent instanceof TvRootItem) {
			return ((TvRootItem) parent).refresh();
		}
		return completedVoid();
	}
}
