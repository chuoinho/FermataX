package me.aap.fermata.addon.radio;

import static me.aap.utils.async.Completed.completed;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

class RadioDirectoryFolder extends RadioFolder {
	static final String ID_COUNTRIES = RadioRootItem.SCHEME + ":countries";
	static final String ID_TAGS = RadioRootItem.SCHEME + ":tags";
	static final String ID_LANGUAGES = RadioRootItem.SCHEME + ":languages";

	enum Kind {
		COUNTRIES,
		TAGS,
		LANGUAGES
	}

	private final Kind kind;

	private RadioDirectoryFolder(String id, RadioRootItem root, String name, Kind kind) {
		super(id, root, name, kind == Kind.TAGS ? me.aap.fermata.R.drawable.playlist :
				me.aap.utils.R.drawable.folder);
		this.kind = kind;
	}

	static RadioDirectoryFolder countries(RadioRootItem root) {
		return new RadioDirectoryFolder(ID_COUNTRIES, root,
				root.getLib().getContext().getString(R.string.radio_countries), Kind.COUNTRIES);
	}

	static RadioDirectoryFolder tags(RadioRootItem root) {
		return new RadioDirectoryFolder(ID_TAGS, root,
				root.getLib().getContext().getString(R.string.radio_tags), Kind.TAGS);
	}

	static RadioDirectoryFolder languages(RadioRootItem root) {
		return new RadioDirectoryFolder(ID_LANGUAGES, root,
				root.getLib().getContext().getString(R.string.radio_languages), Kind.LANGUAGES);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		FutureSupplier<List<RadioBrowserApi.DirectoryEntry>> entries;
		switch (kind) {
			case COUNTRIES -> entries = getRoot().getApi().getCountries();
			case LANGUAGES -> entries = getRoot().getApi().getLanguages();
			case TAGS -> entries = getRoot().getApi().getTags();
			default -> entries = completed(new ArrayList<>());
		}
		return entries.map(list -> {
			List<Item> items = new ArrayList<>(list.size());
			for (RadioBrowserApi.DirectoryEntry entry : list) {
				if (kind == Kind.COUNTRIES) items.add(RadioStationFolder.country(this, entry));
				else if (kind == Kind.LANGUAGES) items.add(RadioStationFolder.language(this, entry));
				else items.add(RadioStationFolder.tag(this, entry));
			}
			return items;
		});
	}
}
