package me.aap.fermata.addon.radio;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

class RadioStationFolder extends RadioFolder {
	static final String ID_POPULAR = RadioRootItem.SCHEME + ":popular";
	static final String ID_TOP_VOTED = RadioRootItem.SCHEME + ":top-voted";
	static final String ID_NEARBY = RadioRootItem.SCHEME + ":near-you";
	static final String ID_NEARBY_PREFIX = RadioRootItem.SCHEME + ":near-you:";
	static final String ID_COUNTRY_PREFIX = RadioRootItem.SCHEME + ":country:";
	static final String ID_TAG_PREFIX = RadioRootItem.SCHEME + ":tag:";
	static final String ID_LANGUAGE_PREFIX = RadioRootItem.SCHEME + ":language:";
	static final int PAGE_SIZE = 100;

	private final Type type;
	private final String value;
	private final int offset;

	RadioStationFolder(String id, BrowsableItem parent, String name, Type type, String value,
										 String subtitle, int offset) {
		super(id, parent, name, me.aap.fermata.R.drawable.radio, subtitle);
		this.type = type;
		this.value = value;
		this.offset = offset;
	}

	static RadioStationFolder popular(RadioRootItem root) {
		return new RadioStationFolder(ID_POPULAR, root,
				root.getLib().getContext().getString(R.string.radio_popular), Type.POPULAR, "", null, 0);
	}

	static RadioStationFolder topVoted(RadioRootItem root) {
		return new RadioStationFolder(ID_TOP_VOTED, root,
				root.getLib().getContext().getString(R.string.radio_top_voted), Type.TOP_VOTED, "", null, 0);
	}

	static RadioStationFolder nearYou(RadioRootItem root, String countryCode) {
		return new RadioStationFolder(nearYouId(countryCode, 0), root,
				root.getLib().getContext().getString(R.string.radio_near_you), Type.NEARBY,
				countryCode, null, 0);
	}

	static RadioStationFolder country(BrowsableItem parent, RadioBrowserApi.DirectoryEntry entry) {
		RadioRootItem root = (RadioRootItem) parent.getRoot();
		return new RadioStationFolder(countryId(entry, 0), parent, entry.name, Type.COUNTRY,
				entry.code, subtitle(root, entry.stationCount), 0);
	}

	static RadioStationFolder language(BrowsableItem parent, RadioBrowserApi.DirectoryEntry entry) {
		RadioRootItem root = (RadioRootItem) parent.getRoot();
		return new RadioStationFolder(languageId(entry, 0), parent, entry.name, Type.LANGUAGE,
				entry.code, subtitle(root, entry.stationCount), 0);
	}

	static RadioStationFolder tag(BrowsableItem parent, RadioBrowserApi.DirectoryEntry entry) {
		RadioRootItem root = (RadioRootItem) parent.getRoot();
		return new RadioStationFolder(tagId(entry.name, 0), parent, entry.name, Type.TAG,
				entry.name, subtitle(root, entry.stationCount), 0);
	}

	static RadioBrowserApi.DirectoryEntry decodeCountry(String id) {
		String tail = id.substring(ID_COUNTRY_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		int sep = tail.indexOf(':');
		if (sep == -1) return new RadioBrowserApi.DirectoryEntry(Uri.decode(tail), Uri.decode(tail), offset);
		String code = Uri.decode(tail.substring(0, sep));
		String name = Uri.decode(tail.substring(sep + 1));
		return new RadioBrowserApi.DirectoryEntry(name, code, offset);
	}

	static RadioBrowserApi.DirectoryEntry decodeLanguage(String id) {
		String tail = id.substring(ID_LANGUAGE_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		int sep = tail.indexOf(':');
		if (sep == -1) return new RadioBrowserApi.DirectoryEntry(Uri.decode(tail), Uri.decode(tail), offset);
		String code = Uri.decode(tail.substring(0, sep));
		String name = Uri.decode(tail.substring(sep + 1));
		return new RadioBrowserApi.DirectoryEntry(name, code, offset);
	}

	static RadioBrowserApi.DirectoryEntry decodeTag(String id) {
		String tail = id.substring(ID_TAG_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		String tag = Uri.decode(tail);
		return new RadioBrowserApi.DirectoryEntry(tag, tag, offset);
	}

	static String decodeNearby(String id) {
		String tail = id.substring(ID_NEARBY_PREFIX.length());
		tail = stripOffset(tail);
		return Uri.decode(tail);
	}

	static int decodeNearbyOffset(String id) {
		String tail = id.substring(ID_NEARBY_PREFIX.length());
		return parseOffset(tail);
	}

	static int parseOffset(String s) {
		int idx = s.lastIndexOf(":offset=");
		if (idx != -1) {
			try {
				return Integer.parseInt(s.substring(idx + 8));
			} catch (NumberFormatException ignored) {
			}
		}
		return 0;
	}

	static String stripOffset(String s) {
		int idx = s.lastIndexOf(":offset=");
		return idx != -1 ? s.substring(0, idx) : s;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		RadioBrowserApi api = getRoot().getApi();
		FutureSupplier<List<RadioBrowserStation>> stations = switch (type) {
			case POPULAR -> api.getPopularStations(PAGE_SIZE);
			case TOP_VOTED -> api.getTopVotedStations(PAGE_SIZE);
			case NEARBY -> api.getCountryStations(value, offset, PAGE_SIZE);
			case COUNTRY -> api.getCountryStations(value, offset, PAGE_SIZE);
			case LANGUAGE -> api.getLanguageStations(value, offset, PAGE_SIZE);
			case TAG -> api.getTagStations(value, offset, PAGE_SIZE);
		};
		return stations.map(list -> {
			List<Item> items = new ArrayList<>(list.size() + 1);
			for (RadioBrowserStation station : list) {
				if (station.hasStreamUrl()) items.add(new RadioStationItem(this, station));
			}
			if (list.size() >= PAGE_SIZE) {
				items.add(createNextPageItem(offset + PAGE_SIZE));
			}
			return items;
		});
	}

	private Item createNextPageItem(int nextOffset) {
		String nextId = switch (type) {
			case COUNTRY -> countryId(new RadioBrowserApi.DirectoryEntry(getName(), value, 0), nextOffset);
			case LANGUAGE -> languageId(new RadioBrowserApi.DirectoryEntry(getName(), value, 0), nextOffset);
			case TAG -> tagId(value, nextOffset);
			case NEARBY -> nearYouId(value, nextOffset);
			default -> getId() + ":offset=" + nextOffset;
		};
		String moreText = getRoot().getLib().getContext().getString(R.string.radio_load_more);
		return new RadioStationFolder(nextId, this, moreText, type, value, null, nextOffset) {
			@Override
			public int getIcon() {
				return me.aap.fermata.R.drawable.more;
			}
		};
	}

	static String countryId(RadioBrowserApi.DirectoryEntry entry, int offset) {
		String base = ID_COUNTRY_PREFIX + Uri.encode(entry.code) + ':' + Uri.encode(entry.name);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	static String languageId(RadioBrowserApi.DirectoryEntry entry, int offset) {
		String base = ID_LANGUAGE_PREFIX + Uri.encode(entry.code) + ':' + Uri.encode(entry.name);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	static String tagId(String tag, int offset) {
		String base = ID_TAG_PREFIX + Uri.encode(tag);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	static String nearYouId(String countryCode, int offset) {
		String base = ID_NEARBY_PREFIX + Uri.encode(countryCode);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	private static String subtitle(RadioRootItem root, int stationCount) {
		return stationCount > 0 ? root.getLib().getContext().getString(R.string.radio_station_count,
				stationCount) : null;
	}

	enum Type {
		POPULAR, TOP_VOTED, COUNTRY, LANGUAGE, TAG, NEARBY
	}
}
