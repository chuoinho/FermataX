package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.utils.async.FutureSupplier;

public class RadioRootItem extends ExtRoot implements RadioItem, SearchFolder.SearchProvider {
	public static final String ID = "Radio";
	static final String SCHEME = "radio";
	private final RadioBrowserApi api;
	private final RadioSourceStore sourceStore;

	public RadioRootItem(DefaultMediaLib lib) {
		super(ID, lib, AddonCapability.RADIO);
		api = new RadioBrowserApi();
		sourceStore = new RadioSourceStore(lib.getContext());
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
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
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
	public FutureSupplier<List<? extends Item>> search(String query, List<? extends Item> localResults) {
		return api.searchByName(query).map(remoteStations -> {
			List<Item> remoteItems = new ArrayList<>(remoteStations.size());
			for (RadioBrowserStation station : remoteStations) {
				if (station.hasStreamUrl()) {
					remoteItems.add(new RadioStationItem(this, station));
				}
			}
			return RadioSearchResultMerger.merge(localResults, remoteItems);
		});
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
			String countryCode = RadioStationFolder.decodeNearby(id);
			int offset = RadioStationFolder.decodeNearbyOffset(id);
			String name = getLib().getContext().getString(R.string.radio_near_you);
			return new RadioStationFolder(id, this, name, RadioStationFolder.Type.NEARBY,
					countryCode, null, offset);
		}
		if (id.startsWith(RadioStationFolder.ID_COUNTRY_PREFIX)) {
			RadioBrowserApi.DirectoryEntry entry = RadioStationFolder.decodeCountry(id);
			return new RadioStationFolder(id, this, entry.name, RadioStationFolder.Type.COUNTRY,
					entry.code, null, entry.stationCount);
		}
		if (id.startsWith(RadioStationFolder.ID_LANGUAGE_PREFIX)) {
			RadioBrowserApi.DirectoryEntry entry = RadioStationFolder.decodeLanguage(id);
			return new RadioStationFolder(id, this, entry.name, RadioStationFolder.Type.LANGUAGE,
					entry.code, null, entry.stationCount);
		}
		if (id.startsWith(RadioStationFolder.ID_TAG_PREFIX)) {
			RadioBrowserApi.DirectoryEntry entry = RadioStationFolder.decodeTag(id);
			return new RadioStationFolder(id, this, entry.name, RadioStationFolder.Type.TAG,
					entry.code, null, entry.stationCount);
		}
		return null;
	}
}
