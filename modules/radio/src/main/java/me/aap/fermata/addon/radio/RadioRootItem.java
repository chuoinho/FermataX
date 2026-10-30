package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

public class RadioRootItem extends ExtRoot implements RadioItem, SearchFolder.SearchProvider {
	public static final String ID = "Radio";
	static final String SCHEME = "radio";
	private final RadioBrowserApi api;
	private final RadioSourceStore sourceStore;

	public RadioRootItem(DefaultMediaLib lib) {
		this((MediaLib) lib, new RadioBrowserApi(), new RadioSourceStore(lib.getContext()));
	}

	RadioRootItem(MediaLib lib, RadioBrowserApi api, RadioSourceStore sourceStore) {
		super(ID, lib, AddonCapability.RADIO);
		this.api = api;
		this.sourceStore = sourceStore;
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(@Nullable String scheme, String id) {
		if (scheme == null) return ID.equals(id) ? completed(this) : null;
		if (!SCHEME.equals(scheme)) return null;
		if (RadioSourceItem.isSourceId(id)) {
			RadioSource source = sourceStore.find(RadioSourceItem.sourceId(id));
			return completed((source == null) ? null : new RadioSourceItem(this, source));
		}
		if (RadioStationItem.isStationId(id)) return RadioStationItem.create(this, id);
		return completed(createFolder(id));
	}

	RadioBrowserApi getApi() {
		return api;
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_radio));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public MediaLib getLib() {
		return super.getLib();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		List<RadioSource> sources = sourceStore.list();
		List<Item> items = new ArrayList<>(sources.size() + 6);
		for (RadioSource source : sources) items.add(new RadioSourceItem(this, source));
		String countryCode = RadioCountryDetector.detectCountryCode(getLib().getContext());
		if (countryCode != null) {
			items.add(RadioStationFolder.nearYou(this, countryCode));
		}
		items.add(RadioStationFolder.popular(this));
		items.add(RadioStationFolder.topVoted(this));
		items.add(RadioDirectoryFolder.countries(this));
		items.add(RadioDirectoryFolder.languages(this));
		items.add(RadioDirectoryFolder.tags(this));
		return completed(items);
	}

	@Override
	public FutureSupplier<List<? extends Item>> search(String query) {
		List<? extends Item> local = matchLocal(query);
		return api.searchByName(query, 0, 50).<List<? extends Item>>map(remoteStations -> {
			List<Item> remoteItems = new ArrayList<>(remoteStations.size());
			for (RadioBrowserStation station : remoteStations) {
				if (station.hasStreamUrl()) {
					remoteItems.add(new RadioStationItem(this, station));
				}
			}
			return RadioSearchResultMerger.merge(local, remoteItems);
		}).ifFail(err -> {
			Log.w(err, "Radio remote search failed");
			return local;
		});
	}

	@Override
	public FutureSupplier<List<? extends Item>> search(String query, List<? extends Item> localResults) {
		return search(query);
	}

	private List<Item> matchLocal(String query) {
		String q = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return Collections.emptyList();
		String[] words = q.split("\\s+");
		List<RadioSource> sources = sourceStore.list();
		List<Item> matched = new ArrayList<>();
		for (RadioSource source : sources) {
			String name = source.getName().toLowerCase(Locale.ROOT);
			if (matches(name, q, words)) {
				matched.add(new RadioSourceItem(this, source));
			}
		}
		return matched;
	}

	private static boolean matches(String name, String query, String[] words) {
		if (name.contains(query)) return true;
		for (String word : words) {
			if (!name.contains(word)) return false;
		}
		return true;
	}

	void saveSource(RadioSource source) {
		sourceStore.save(source);
		invalidateSearch();
	}

	@Nullable
	RadioSource findSource(String id) {
		return sourceStore.find(id);
	}

	FutureSupplier<Void> removeSource(RadioSource source) {
		RadioSourceItem item = new RadioSourceItem(this, source);
		MediaLib lib = getLib();
		return lib.getRecent().removeItem(item)
				.then(ignored -> lib.getFavorites().removeItem(item))
				.thenRun(() -> {
					clearLastPlayed(item.getId(), lib.getPrefs());
					clearLastPlayed(item.getId(), getPrefs());
					sourceStore.remove(source);
					invalidateSearch();
				});
	}

	void invalidateSearch() {
		SearchFolder.invalidate(this);
	}

	private static void clearLastPlayed(String itemId,
			me.aap.fermata.media.pref.BrowsableItemPrefs prefs) {
		if (!itemId.equals(prefs.getLastPlayedItemPref())) return;
		prefs.setLastPlayedItemPref(null);
		prefs.setLastPlayedPosPref(0);
	}

	boolean isChildItemId(String id) {
		return ID.equals(id) || id.startsWith(SCHEME + ':');
	}

	@Nullable
	private MediaLib.Item createFolder(String id) {
		if (RadioStationFolder.ID_POPULAR.equals(id)) return RadioStationFolder.popular(this);
		if (RadioStationFolder.ID_TOP_VOTED.equals(id)) return RadioStationFolder.topVoted(this);
		if (RadioDirectoryFolder.ID_COUNTRIES.equals(id)) return RadioDirectoryFolder.countries(this);
		if (RadioDirectoryFolder.ID_LANGUAGES.equals(id)) return RadioDirectoryFolder.languages(this);
		if (RadioDirectoryFolder.ID_TAGS.equals(id)) return RadioDirectoryFolder.tags(this);
		if (id.startsWith(RadioStationFolder.ID_NEARBY_PREFIX)) {
			RadioStationFolder.FolderSpec spec = RadioStationFolder.decodeNearby(id);
			String name = getLib().getContext().getString(R.string.radio_near_you);
			return new RadioStationFolder(id, this, name, RadioStationFolder.Type.NEARBY,
					spec.value, null, spec.offset);
		}
		if (id.startsWith(RadioStationFolder.ID_COUNTRY_PREFIX)) {
			RadioStationFolder.FolderSpec spec = RadioStationFolder.decodeCountry(id);
			return new RadioStationFolder(id, this, spec.name, RadioStationFolder.Type.COUNTRY,
					spec.value, null, spec.offset);
		}
		if (id.startsWith(RadioStationFolder.ID_LANGUAGE_PREFIX)) {
			RadioStationFolder.FolderSpec spec = RadioStationFolder.decodeLanguage(id);
			return new RadioStationFolder(id, this, spec.name, RadioStationFolder.Type.LANGUAGE,
					spec.value, null, spec.offset);
		}
		if (id.startsWith(RadioStationFolder.ID_TAG_PREFIX)) {
			RadioStationFolder.FolderSpec spec = RadioStationFolder.decodeTag(id);
			return new RadioStationFolder(id, this, spec.name, RadioStationFolder.Type.TAG,
					spec.value, null, spec.offset);
		}
		return null;
	}
}
