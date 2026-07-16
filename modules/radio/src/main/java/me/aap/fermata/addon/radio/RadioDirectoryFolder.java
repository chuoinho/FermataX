package me.aap.fermata.addon.radio;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

class RadioDirectoryFolder extends RadioFolder {
	static final String ID_COUNTRIES = RadioRootItem.SCHEME + ":countries";
	static final String ID_TAGS = RadioRootItem.SCHEME + ":tags";
	private final boolean countries;

	private RadioDirectoryFolder(String id, RadioRootItem root, String name, boolean countries) {
		super(id, root, name, countries ? me.aap.utils.R.drawable.folder :
				me.aap.fermata.R.drawable.playlist);
		this.countries = countries;
	}

	static RadioDirectoryFolder countries(RadioRootItem root) {
		return new RadioDirectoryFolder(ID_COUNTRIES, root,
				root.getLib().getContext().getString(R.string.radio_countries), true);
	}

	static RadioDirectoryFolder tags(RadioRootItem root) {
		return new RadioDirectoryFolder(ID_TAGS, root,
				root.getLib().getContext().getString(R.string.radio_tags), false);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		FutureSupplier<List<RadioBrowserApi.DirectoryEntry>> entries = countries ?
				getRoot().getApi().getCountries(80) : getRoot().getApi().getTags(100);
		return entries.map(list -> {
			List<Item> items = new ArrayList<>(list.size());
			for (RadioBrowserApi.DirectoryEntry entry : list) {
				if (countries) items.add(RadioStationFolder.country(this, entry));
				else items.add(RadioStationFolder.tag(this, entry));
			}
			return items;
		});
	}
}
