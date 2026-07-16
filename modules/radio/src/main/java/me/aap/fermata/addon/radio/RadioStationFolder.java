package me.aap.fermata.addon.radio;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;

class RadioStationFolder extends RadioFolder {
	static final String ID_POPULAR = RadioRootItem.SCHEME + ":popular";
	static final String ID_TOP_VOTED = RadioRootItem.SCHEME + ":top-voted";
	static final String ID_COUNTRY_PREFIX = RadioRootItem.SCHEME + ":country:";
	static final String ID_TAG_PREFIX = RadioRootItem.SCHEME + ":tag:";
	private static final int STATION_LIMIT = 120;
	private final Type type;
	private final String value;

	private RadioStationFolder(String id, BrowsableItem parent, String name, Type type, String value,
														 String subtitle) {
		super(id, parent, name, me.aap.fermata.R.drawable.radio, subtitle);
		this.type = type;
		this.value = value;
	}

	static RadioStationFolder popular(RadioRootItem root) {
		return new RadioStationFolder(ID_POPULAR, root,
				root.getLib().getContext().getString(R.string.radio_popular), Type.POPULAR, "", null);
	}

	static RadioStationFolder topVoted(RadioRootItem root) {
		return new RadioStationFolder(ID_TOP_VOTED, root,
				root.getLib().getContext().getString(R.string.radio_top_voted), Type.TOP_VOTED, "", null);
	}

	static RadioStationFolder country(BrowsableItem parent, RadioBrowserApi.DirectoryEntry entry) {
		RadioRootItem root = (RadioRootItem) parent.getRoot();
		return new RadioStationFolder(countryId(entry), parent, entry.name, Type.COUNTRY,
				entry.code, subtitle(root, entry.stationCount));
	}

	static RadioStationFolder tag(BrowsableItem parent, RadioBrowserApi.DirectoryEntry entry) {
		RadioRootItem root = (RadioRootItem) parent.getRoot();
		return new RadioStationFolder(tagId(entry.name), parent, entry.name, Type.TAG,
				entry.name, subtitle(root, entry.stationCount));
	}

	static RadioBrowserApi.DirectoryEntry decodeCountry(String id) {
		String tail = id.substring(ID_COUNTRY_PREFIX.length());
		int sep = tail.indexOf(':');
		if (sep == -1) return new RadioBrowserApi.DirectoryEntry(Uri.decode(tail), Uri.decode(tail), 0);
		String code = Uri.decode(tail.substring(0, sep));
		String name = Uri.decode(tail.substring(sep + 1));
		return new RadioBrowserApi.DirectoryEntry(name, code, 0);
	}

	static RadioBrowserApi.DirectoryEntry decodeTag(String id) {
		String tag = Uri.decode(id.substring(ID_TAG_PREFIX.length()));
		return new RadioBrowserApi.DirectoryEntry(tag, tag, 0);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		RadioBrowserApi api = getRoot().getApi();
		FutureSupplier<List<RadioBrowserStation>> stations = switch (type) {
			case POPULAR -> api.getPopularStations(STATION_LIMIT);
			case TOP_VOTED -> api.getTopVotedStations(STATION_LIMIT);
			case COUNTRY -> api.getCountryStations(value, STATION_LIMIT);
			case TAG -> api.getTagStations(value, STATION_LIMIT);
		};
		return stations.map(list -> {
			List<Item> items = new ArrayList<>(list.size());
			for (RadioBrowserStation station : list) {
				if (station.hasStreamUrl()) items.add(new RadioStationItem(this, station));
			}
			return items;
		});
	}

	private static String countryId(RadioBrowserApi.DirectoryEntry entry) {
		return ID_COUNTRY_PREFIX + Uri.encode(entry.code) + ':' + Uri.encode(entry.name);
	}

	private static String tagId(String tag) {
		return ID_TAG_PREFIX + Uri.encode(tag);
	}

	private static String subtitle(RadioRootItem root, int stationCount) {
		return stationCount > 0 ? root.getLib().getContext().getString(R.string.radio_station_count,
				stationCount) : null;
	}

	private enum Type {
		POPULAR, TOP_VOTED, COUNTRY, TAG
	}
}
