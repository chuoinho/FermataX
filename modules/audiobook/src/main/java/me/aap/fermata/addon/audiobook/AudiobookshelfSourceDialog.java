package me.aap.fermata.addon.audiobook;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceViewAdapter;
import me.aap.utils.ui.fragment.GenericDialogFragment;

final class AudiobookshelfSourceDialog {
	private static final PreferenceStore.Pref<Supplier<String>> URL =
			PreferenceStore.Pref.s("audiobookshelf-url", "");
	private static final PreferenceStore.Pref<Supplier<String>> USERNAME =
			PreferenceStore.Pref.s("audiobookshelf-username", "");
	private static final PreferenceStore.Pref<Supplier<String>> PASSWORD =
			PreferenceStore.Pref.s("audiobookshelf-password", "");

	private AudiobookshelfSourceDialog() {
	}

	static FutureSupplier<Config> show(MainActivityDelegate activity) {
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment fragment)) {
			return me.aap.utils.async.Completed.cancelled();
		}
		BasicPreferenceStore store = new BasicPreferenceStore();
		PreferenceSet preferences = createPreferences(store);
		PreferenceViewAdapter adapter = new PreferenceViewAdapter(preferences);
		Promise<Config> result = new Promise<>();

		fragment.setTitle(activity.getString(R.string.audiobook_add_audiobookshelf));
		fragment.setContentProvider(group -> {
			RecyclerView view = new RecyclerView(group.getContext());
			view.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			view.setHasFixedSize(true);
			view.setLayoutManager(new LinearLayoutManager(group.getContext()));
			view.setAdapter(adapter);
			group.addView(view);
		});
		fragment.setDialogValidator(() -> isValid(store));
		fragment.setDialogConsumer(ok -> {
			store.removeBroadcastListeners();
			if (ok) result.complete(config(store));
			else result.cancel();
		});
		fragment.setBackHandler(() -> {
			store.removeBroadcastListeners();
			result.cancel();
			return false;
		});
		store.addBroadcastListener((ignored, changed) -> activity.fireBroadcastEvent(
				FRAGMENT_CONTENT_CHANGED));
		return result;
	}

	private static PreferenceSet createPreferences(PreferenceStore store) {
		PreferenceSet preferences = new PreferenceSet();
		preferences.addStringPref(options -> {
			options.store = store;
			options.pref = URL;
			options.title = R.string.audiobook_server_url;
			options.stringHint = "https://books.example.com";
			options.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI |
					TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			options.imeOptions = EditorInfo.IME_ACTION_NEXT;
			options.selectAllOnFocus = true;
			options.trim = true;
		});
		preferences.addStringPref(options -> {
			options.store = store;
			options.pref = USERNAME;
			options.title = me.aap.fermata.R.string.username;
			options.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			options.imeOptions = EditorInfo.IME_ACTION_NEXT;
			options.selectAllOnFocus = true;
			options.trim = true;
		});
		preferences.addStringPref(options -> {
			options.store = store;
			options.pref = PASSWORD;
			options.title = me.aap.fermata.R.string.password;
			options.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD |
					TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			options.imeOptions = EditorInfo.IME_ACTION_DONE;
			options.selectAllOnFocus = true;
			options.submitOnEnter = true;
			options.trim = true;
		});
		return preferences;
	}

	private static boolean isValid(PreferenceStore store) {
		return !value(store.getStringPref(URL)).isEmpty() &&
				!value(store.getStringPref(USERNAME)).isEmpty() &&
				!value(store.getStringPref(PASSWORD)).isEmpty();
	}

	private static Config config(PreferenceStore store) {
		return new Config(value(store.getStringPref(URL)), value(store.getStringPref(USERNAME)),
				value(store.getStringPref(PASSWORD)));
	}

	private static String value(String value) {
		return (value == null) ? "" : value.trim();
	}

	record Config(String endpoint, String username, String password) {
	}
}
