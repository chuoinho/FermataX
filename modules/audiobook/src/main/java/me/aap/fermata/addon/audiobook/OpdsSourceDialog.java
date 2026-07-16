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

final class OpdsSourceDialog {
	private static final PreferenceStore.Pref<Supplier<String>> URL =
			PreferenceStore.Pref.s("opds-url", "");
	private static final PreferenceStore.Pref<Supplier<String>> USERNAME =
			PreferenceStore.Pref.s("opds-username", "");
	private static final PreferenceStore.Pref<Supplier<String>> PASSWORD =
			PreferenceStore.Pref.s("opds-password", "");
	private static final PreferenceStore.Pref<Supplier<String>> TOKEN =
			PreferenceStore.Pref.s("opds-token", "");

	private OpdsSourceDialog() {
	}

	static FutureSupplier<Config> show(MainActivityDelegate activity) {
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment fragment)) {
			return me.aap.utils.async.Completed.cancelled();
		}
		BasicPreferenceStore store = new BasicPreferenceStore();
		PreferenceViewAdapter adapter = new PreferenceViewAdapter(createPreferences(store));
		Promise<Config> result = new Promise<>();
		fragment.setTitle(activity.getString(R.string.audiobook_add_opds));
		fragment.setContentProvider(group -> {
			RecyclerView view = new RecyclerView(group.getContext());
			view.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			view.setHasFixedSize(true);
			view.setLayoutManager(new LinearLayoutManager(group.getContext()));
			view.setAdapter(adapter);
			group.addView(view);
		});
		fragment.setDialogValidator(() -> valid(store));
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
		PreferenceSet set = new PreferenceSet();
		set.addStringPref(o -> {
			o.store = store; o.pref = URL; o.title = R.string.audiobook_server_url;
			o.stringHint = "https://example.com/opds";
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT; o.selectAllOnFocus = true; o.trim = true;
		});
		set.addStringPref(o -> {
			o.store = store; o.pref = USERNAME; o.title = me.aap.fermata.R.string.username;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT; o.selectAllOnFocus = true; o.trim = true;
		});
		set.addStringPref(o -> {
			o.store = store; o.pref = PASSWORD; o.title = me.aap.fermata.R.string.password;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT; o.selectAllOnFocus = true; o.trim = true;
		});
		set.addStringPref(o -> {
			o.store = store; o.pref = TOKEN; o.title = R.string.audiobook_bearer_token;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_DONE; o.selectAllOnFocus = true;
			o.submitOnEnter = true; o.trim = true;
		});
		return set;
	}

	private static boolean valid(PreferenceStore store) {
		if (value(store.getStringPref(URL)).isEmpty()) return false;
		if (!value(store.getStringPref(TOKEN)).isEmpty()) return true;
		boolean username = !value(store.getStringPref(USERNAME)).isEmpty();
		boolean password = !value(store.getStringPref(PASSWORD)).isEmpty();
		return username == password;
	}

	private static Config config(PreferenceStore store) {
		return new Config(value(store.getStringPref(URL)), value(store.getStringPref(USERNAME)),
				value(store.getStringPref(PASSWORD)), value(store.getStringPref(TOKEN)));
	}

	private static String value(String value) {
		return (value == null) ? "" : value.trim();
	}

	record Config(String endpoint, String username, String password, String bearerToken) {
	}
}
