package me.aap.fermata.addon.tv.stalker;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import java.io.IOException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;

public final class StalkerFileSystemProvider extends VfsProviderBase {
	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(Context context,
			Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore store) {
		return completedNull();
	}

	public FutureSupplier<StalkerAccount> select(MainActivityDelegate activity) {
		BasicPreferenceStore store = new BasicPreferenceStore();
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			edit.setStringPref(AGENT, StalkerAccount.DEFAULT_USER_AGENT);
			edit.setIntPref(RESP_TIMEOUT, 30);
		}
		return requestPrefs(activity, store).thenRun(store::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return validate(activity.getContext(), account(store));
		});
	}

	public FutureSupplier<StalkerAccount> edit(MainActivityDelegate activity,
			StalkerAccount account) {
		BasicPreferenceStore store = new BasicPreferenceStore();
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			edit.setStringPref(StalkerAccount.NAME, account.getRawName());
			edit.setStringPref(StalkerAccount.PORTAL, account.getPortal());
			edit.setStringPref(StalkerAccount.MAC, account.getMac());
			edit.setStringPref(StalkerAccount.SERIAL, account.getSerial());
			edit.setStringPref(StalkerAccount.DEVICE_ID, account.getDeviceId());
			edit.setStringPref(AGENT, account.getRawUserAgent());
			edit.setIntPref(RESP_TIMEOUT, account.getResponseTimeout());
		}
		return requestPrefs(activity, store).thenRun(store::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return validate(activity.getContext(), account(store).withSourceId(account.getSourceId()));
		});
	}

	private FutureSupplier<StalkerAccount> validate(Context context, StalkerAccount account) {
		if (!account.isComplete()) {
			return failed(new IOException(context.getString(
					R.string.stalker_error_incomplete_account)));
		}
		return new StalkerApi(account, context).healthCheck().main().map(health -> {
			if (health.isDegraded()) {
				UiUtils.showAlert(context, context.getString(R.string.stalker_health_degraded,
						health.getCategoryCount(), health.getChannelCount()));
			}
			return account;
		});
	}

	private StalkerAccount account(PreferenceStore store) {
		return StalkerAccount.fromPrefs(store, store.getStringPref(AGENT),
				store.getIntPref(RESP_TIMEOUT));
	}

	private FutureSupplier<Boolean> requestPrefs(MainActivityDelegate activity,
			PreferenceStore store) {
		PreferenceSet preferences = new PreferenceSet();
		preferences.addStringPref(option -> {
			option.store = store;
			option.pref = StalkerAccount.NAME;
			option.title = R.string.tv_source_name;
			option.imeOptions = EditorInfo.IME_ACTION_NEXT;
			option.selectAllOnFocus = true;
		});
		preferences.addStringPref(option -> {
			option.store = store;
			option.pref = StalkerAccount.PORTAL;
			option.title = R.string.stalker_portal_url;
			option.stringHint = "http://host/stalker_portal/c/";
			option.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI |
					TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			option.imeOptions = EditorInfo.IME_ACTION_NEXT;
			option.selectAllOnFocus = true;
			option.trim = true;
		});
		preferences.addStringPref(option -> {
			option.store = store;
			option.pref = StalkerAccount.MAC;
			option.title = R.string.stalker_mac_address;
			option.stringHint = "00:1A:79:00:00:00";
			option.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			option.imeOptions = EditorInfo.IME_ACTION_DONE;
			option.selectAllOnFocus = true;
			option.submitOnEnter = true;
			option.trim = true;
		});

		PreferenceSet identity = preferences.subSet(option ->
				option.title = R.string.stalker_device_identity);
		identity.addStringPref(option -> {
			option.store = store;
			option.pref = StalkerAccount.SERIAL;
			option.title = R.string.stalker_serial_number;
			option.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			option.imeOptions = EditorInfo.IME_ACTION_NEXT;
			option.selectAllOnFocus = true;
			option.trim = true;
		});
		identity.addStringPref(option -> {
			option.store = store;
			option.pref = StalkerAccount.DEVICE_ID;
			option.title = R.string.stalker_device_id;
			option.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			option.imeOptions = EditorInfo.IME_ACTION_DONE;
			option.selectAllOnFocus = true;
			option.trim = true;
		});

		PreferenceSet connection = preferences.subSet(option ->
				option.title = R.string.connection_settings);
		connection.addStringPref(option -> {
			option.store = store;
			option.pref = AGENT;
			option.title = me.aap.fermata.R.string.m3u_playlist_agent;
			option.stringHint = "Fermata/" + BuildConfig.VERSION_NAME;
			option.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			option.imeOptions = EditorInfo.IME_ACTION_DONE;
			option.selectAllOnFocus = true;
		});
		connection.addIntPref(option -> {
			option.store = store;
			option.pref = RESP_TIMEOUT;
			option.title = me.aap.fermata.R.string.m3u_playlist_timeout;
			option.imeOptions = EditorInfo.IME_ACTION_DONE;
			option.selectAllOnFocus = true;
			option.submitOnEnter = true;
		});
		return requestPrefs(activity, preferences, store);
	}

	@Override
	protected boolean validate(PreferenceStore store) {
		return account(store).isComplete();
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	@Override
	protected String getTitle(MainActivityDelegate activity) {
		return activity.getString(R.string.add_stalker_source);
	}
}
