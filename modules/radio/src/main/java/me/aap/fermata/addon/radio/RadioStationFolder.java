package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
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

	private final String folderName;
	private final Type type;
	private final String value;
	private final int offset;

	RadioStationFolder(String id, BrowsableItem parent, String name, String folderName, Type type,
										 String value, String subtitle, int offset) {
		super(id, parent, name, me.aap.fermata.R.drawable.radio, subtitle);
		this.folderName = folderName;
		this.type = type;
		this.value = value;
		this.offset = offset;
	}

	RadioStationFolder(String id, BrowsableItem parent, String name, Type type, String value,
										 String subtitle, int offset) {
		this(id, parent, name, name, type, value, subtitle, offset);
	}

	int getOffset() {
		return offset;
	}

	String getFolderName() {
		return folderName;
	}

	static RadioStationFolder popular(RadioRootItem root) {
		String name = root.getLib().getContext().getString(R.string.radio_popular);
		return new RadioStationFolder(ID_POPULAR, root, name, Type.POPULAR, "", null, 0);
	}

	static RadioStationFolder topVoted(RadioRootItem root) {
		String name = root.getLib().getContext().getString(R.string.radio_top_voted);
		return new RadioStationFolder(ID_TOP_VOTED, root, name, Type.TOP_VOTED, "", null, 0);
	}

	static RadioStationFolder nearYou(RadioRootItem root, String countryCode) {
		String name = root.getLib().getContext().getString(R.string.radio_near_you);
		return new RadioStationFolder(nearYouId(countryCode, 0), root, name, Type.NEARBY,
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

	static FolderSpec decodeCountry(String id) {
		String tail = id.substring(ID_COUNTRY_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		int sep = tail.indexOf(':');
		if (sep == -1) return new FolderSpec(Uri.decode(tail), Uri.decode(tail), offset);
		String code = Uri.decode(tail.substring(0, sep));
		String name = Uri.decode(tail.substring(sep + 1));
		return new FolderSpec(name, code, offset);
	}

	static FolderSpec decodeLanguage(String id) {
		String tail = id.substring(ID_LANGUAGE_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		int sep = tail.indexOf(':');
		if (sep == -1) return new FolderSpec(Uri.decode(tail), Uri.decode(tail), offset);
		String code = Uri.decode(tail.substring(0, sep));
		String name = Uri.decode(tail.substring(sep + 1));
		return new FolderSpec(name, code, offset);
	}

	static FolderSpec decodeTag(String id) {
		String tail = id.substring(ID_TAG_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		String tag = Uri.decode(tail);
		return new FolderSpec(tag, tag, offset);
	}

	static FolderSpec decodeNearby(String id) {
		String tail = id.substring(ID_NEARBY_PREFIX.length());
		int offset = parseOffset(tail);
		tail = stripOffset(tail);
		return new FolderSpec("", Uri.decode(tail), offset);
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
			boolean hasMore = (type == Type.COUNTRY || type == Type.LANGUAGE || type == Type.TAG || type == Type.NEARBY)
					&& list.size() >= PAGE_SIZE;
			if (hasMore) {
				items.add(createNextPageItem(offset + PAGE_SIZE));
			}
			return items;
		});
	}

	private Item createNextPageItem(int nextOffset) {
		String nextId = switch (type) {
			case COUNTRY -> countryId(folderName, value, nextOffset);
			case LANGUAGE -> languageId(folderName, value, nextOffset);
			case TAG -> tagId(folderName, nextOffset);
			case NEARBY -> nearYouId(value, nextOffset);
			default -> getId() + ":offset=" + nextOffset;
		};
		String moreText = getRoot().getLib().getContext().getString(R.string.radio_load_more);
		return new RadioStationFolder(nextId, this, folderName, folderName, type, value, null, nextOffset) {
			@Override
			public int getIcon() {
				return me.aap.fermata.R.drawable.more;
			}

			@NonNull
			@Override
			protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
				return completed(moreText);
			}
		};
	}

	static String countryId(String name, String code, int offset) {
		String base = ID_COUNTRY_PREFIX + Uri.encode(code) + ':' + Uri.encode(name);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	static String countryId(RadioBrowserApi.DirectoryEntry entry, int offset) {
		return countryId(entry.name, entry.code, offset);
	}

	static String languageId(String name, String code, int offset) {
		String base = ID_LANGUAGE_PREFIX + Uri.encode(code) + ':' + Uri.encode(name);
		return offset > 0 ? base + ":offset=" + offset : base;
	}

	static String languageId(RadioBrowserApi.DirectoryEntry entry, int offset) {
		return languageId(entry.name, entry.code, offset);
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

	static final class FolderSpec {
		final String name;
		final String value;
		final int offset;

		FolderSpec(String name, String value, int offset) {
			this.name = name;
			this.value = value;
			this.offset = offset;
		}
	}

	enum Type {
		POPULAR, TOP_VOTED, COUNTRY, LANGUAGE, TAG, NEARBY
	}
}
