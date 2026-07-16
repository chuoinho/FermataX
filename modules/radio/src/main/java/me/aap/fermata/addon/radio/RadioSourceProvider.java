package me.aap.fermata.addon.radio;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;

final class RadioSourceProvider extends VfsProviderBase {
	private static final Pref<Supplier<String>> NAME = Pref.s("RADIO_SOURCE_NAME");
	private static final Pref<Supplier<String>> URL = Pref.s("RADIO_SOURCE_URL");
	private final RadioSource source;

	RadioSourceProvider() {
		this(null);
	}

	RadioSourceProvider(RadioSource source) {
		this.source = source;
	}

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(Context context,
			Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore preferences) {
		return completedNull();
	}

	FutureSupplier<RadioSource> select(MainActivityDelegate activity) {
		BasicPreferenceStore store = new BasicPreferenceStore();
		if (source != null) {
			try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
				edit.setStringPref(NAME, source.getName());
				edit.setStringPref(URL, source.getUrl());
			}
		}

		PreferenceSet preferences = new PreferenceSet();
		preferences.addStringPref(opts -> {
			opts.store = store;
			opts.pref = NAME;
			opts.title = R.string.radio_source_name;
			opts.imeOptions = EditorInfo.IME_ACTION_NEXT;
			opts.selectAllOnFocus = true;
			opts.trim = true;
		});
		preferences.addStringPref(opts -> {
			opts.store = store;
			opts.pref = URL;
			opts.title = R.string.radio_source_url;
			opts.stringHint = "https://example.com/live/stream.mp3";
			opts.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			opts.imeOptions = EditorInfo.IME_ACTION_DONE;
			opts.selectAllOnFocus = true;
			opts.submitOnEnter = true;
			opts.trim = true;
		});

		return requestPrefs(activity, preferences, store).thenRun(store::removeBroadcastListeners)
				.map(ok -> {
					if (!ok) return null;
					String name = store.getStringPref(NAME);
					String url = store.getStringPref(URL);
					return (source == null) ? RadioSource.create(name, url) : source.update(name, url);
				});
	}

	@Override
	protected boolean validate(PreferenceStore store) {
		return RadioSource.restore("validation", store.getStringPref(NAME),
				store.getStringPref(URL)).isValid();
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	@Override
	protected String getTitle(MainActivityDelegate activity) {
		return activity.getString((source == null) ? R.string.radio_add_source : R.string.radio_edit_source);
	}
}
