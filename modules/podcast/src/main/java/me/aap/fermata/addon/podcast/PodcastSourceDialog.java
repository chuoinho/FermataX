package me.aap.fermata.addon.podcast;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.inputmethod.EditorInfo;

import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.addon.podcast.model.PodcastSource;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceViewAdapter;
import me.aap.utils.ui.fragment.GenericDialogFragment;

final class PodcastSourceDialog {
	private static final PreferenceStore.Pref<Supplier<String>> URL =
			PreferenceStore.Pref.s("podcast-url", "");
	private static final PreferenceStore.Pref<Supplier<String>> USERNAME =
			PreferenceStore.Pref.s("podcast-username", "");
	private static final PreferenceStore.Pref<Supplier<String>> PASSWORD =
			PreferenceStore.Pref.s("podcast-password", "");

	private PodcastSourceDialog() {
	}

	static FutureSupplier<PodcastSource> show(MainActivityDelegate activity,
			@StringRes int title, String initialUrl) {
		if (!(activity.showFragment(me.aap.utils.R.id.generic_dialog_fragment)
				instanceof GenericDialogFragment fragment)) {
			return me.aap.utils.async.Completed.cancelled();
		}
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyStringPref(URL, (initialUrl == null) ? "" : initialUrl);
		PreferenceSet preferences = createPreferences(store);
		PreferenceViewAdapter adapter = new PreferenceViewAdapter(preferences);
		Promise<PodcastSource> result = new Promise<>();

		fragment.setTitle(activity.getString(title));
		fragment.setContentProvider(group -> {
			RecyclerView view = new RecyclerView(group.getContext());
			view.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			view.setHasFixedSize(true);
			view.setLayoutManager(new LinearLayoutManager(group.getContext()));
			view.setAdapter(adapter);
			group.addView(view);
		});
		fragment.setDialogValidator(() -> source(store) != null);
		fragment.setDialogConsumer(ok -> {
			store.removeBroadcastListeners();
			if (ok) result.complete(source(store));
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
			options.title = R.string.podcast_feed_url;
			options.stringHint = "https://example.com/podcast.rss";
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

	private static PodcastSource source(PreferenceStore store) {
		String username = store.getStringPref(USERNAME);
		return PodcastSource.create(store.getStringPref(URL),
				(username == null) ? null : username.trim(), store.getStringPref(PASSWORD));
	}
}
