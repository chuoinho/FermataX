package me.aap.fermata.addon.tv.xtream;

import static me.aap.fermata.addon.tv.xtream.XtreamAccount.HOST;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.NAME;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.OUTPUT;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.PASSWORD;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.PORT;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.SCHEME;
import static me.aap.fermata.addon.tv.xtream.XtreamAccount.USERNAME;
import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import java.io.IOException;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class XtreamFileSystemProvider extends VfsProviderBase {

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier,
			PreferenceStore ps) {
		return completedNull();
	}

	public FutureSupplier<XtreamAccount> select(MainActivityDelegate a) {
		PreferenceStore ps = PrefsHolder.instance;
		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return validate(XtreamAccount.fromPrefs(ps, ps.getStringPref(AGENT),
					ps.getIntPref(RESP_TIMEOUT)));
		});
	}

	public FutureSupplier<XtreamAccount> edit(MainActivityDelegate a, XtreamAccount account) {
		BasicPreferenceStore ps = new BasicPreferenceStore();

		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.setStringPref(NAME, account.getRawName());
			e.setIntPref(SCHEME, account.getSchemeIndex());
			e.setStringPref(HOST, account.getHost());
			e.setIntPref(PORT, account.getPort());
			e.setStringPref(USERNAME, account.getUsername());
			e.setStringPref(PASSWORD, account.getPassword());
			e.setIntPref(OUTPUT, account.getOutputIndex());
			e.setStringPref(AGENT, account.getUserAgent());
			e.setIntPref(RESP_TIMEOUT, account.getResponseTimeout());
		}

		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return validate(XtreamAccount.fromPrefs(ps, ps.getStringPref(AGENT),
					ps.getIntPref(RESP_TIMEOUT)).withSourceId(account.getSourceId()));
		});
	}

	private FutureSupplier<XtreamAccount> validate(XtreamAccount account) {
		if (!account.isComplete()) {
			return failed(new IOException("Enter host, username and password."));
		}
		return new XtreamApi(account).healthCheck().map(health -> account);
	}

	private FutureSupplier<Boolean> requestPrefs(MainActivityDelegate a, PreferenceStore ps) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceSet sub;

		installPortalAutoFill(ps);

		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = NAME;
			o.title = R.string.tv_source_name;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT;
			o.selectAllOnFocus = true;
		});
		prefs.addListPref(o -> {
			o.store = ps;
			o.pref = SCHEME;
			o.title = R.string.xtream_scheme;
			o.subtitle = R.string.xtream_scheme_cur;
			o.stringValues = XtreamAccount.SCHEMES;
			o.formatSubtitle = true;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = HOST;
			o.title = me.aap.fermata.R.string.host;
			o.stringHint = "http://host:port/get.php?username=...&password=...";
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT;
			o.selectAllOnFocus = true;
			o.trim = true;
		});
		prefs.addIntPref(o -> {
			o.store = ps;
			o.pref = PORT;
			o.title = me.aap.fermata.R.string.port;
			o.ems = 5;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT;
			o.selectAllOnFocus = true;
			o.showProgress = false;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = USERNAME;
			o.title = me.aap.fermata.R.string.username;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_NEXT;
			o.selectAllOnFocus = true;
			o.trim = true;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PASSWORD;
			o.title = me.aap.fermata.R.string.password;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_DONE;
			o.selectAllOnFocus = true;
			o.submitOnEnter = true;
			o.trim = true;
		});
		prefs.addListPref(o -> {
			o.store = ps;
			o.pref = OUTPUT;
			o.title = R.string.xtream_output;
			o.subtitle = R.string.xtream_output_cur;
			o.stringValues = XtreamAccount.OUTPUTS;
			o.formatSubtitle = true;
		});

		sub = prefs.subSet(o -> o.title = R.string.connection_settings);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = AGENT;
			o.title = me.aap.fermata.R.string.m3u_playlist_agent;
			o.stringHint = "Fermata/" + BuildConfig.VERSION_NAME;
			o.inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS;
			o.imeOptions = EditorInfo.IME_ACTION_DONE;
			o.selectAllOnFocus = true;
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = RESP_TIMEOUT;
			o.title = me.aap.fermata.R.string.m3u_playlist_timeout;
			o.imeOptions = EditorInfo.IME_ACTION_DONE;
			o.selectAllOnFocus = true;
			o.submitOnEnter = true;
		});

		return requestPrefs(a, prefs, ps);
	}

	private void installPortalAutoFill(PreferenceStore ps) {
		boolean[] updating = new boolean[1];
		ps.addBroadcastListener((store, changed) -> {
			if (updating[0] || !changed.contains(HOST)) return;

			String value = ps.getStringPref(HOST);
			if (!looksLikePortalInput(value)) return;

			XtreamAccount parsed = new XtreamAccount(0, null, ps.getIntPref(SCHEME), value,
					ps.getIntPref(PORT), null, null, ps.getIntPref(OUTPUT), null, 0);
			boolean hasCredential = !TextUtils.isNullOrBlank(parsed.getUsername()) ||
					!TextUtils.isNullOrBlank(parsed.getPassword());
			boolean hasHost = !TextUtils.isNullOrBlank(parsed.getHost());
			if (!hasHost && !hasCredential) return;

			updating[0] = true;
			try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
				if (hasHost) e.setStringPref(HOST, parsed.getHost());
				e.setIntPref(SCHEME, parsed.getSchemeIndex());
				if (parsed.getPort() > 0) e.setIntPref(PORT, parsed.getPort());
				if (!TextUtils.isNullOrBlank(parsed.getUsername())) {
					e.setStringPref(USERNAME, parsed.getUsername());
				}
				if (!TextUtils.isNullOrBlank(parsed.getPassword())) {
					e.setStringPref(PASSWORD, parsed.getPassword());
				}
				e.setIntPref(OUTPUT, parsed.getOutputIndex());
			} finally {
				updating[0] = false;
			}
		});
	}

	private boolean looksLikePortalInput(String value) {
		if (TextUtils.isNullOrBlank(value)) return false;
		String v = value.toLowerCase(Locale.ROOT);
		return v.startsWith("//") || v.contains("://") || v.contains("get.php") ||
				v.contains("player_api.php") || v.contains("username=") || v.contains("password=") ||
				v.contains("user=") || v.contains("pass=") || v.contains("@");
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		return XtreamAccount.fromPrefs(ps, ps.getStringPref(AGENT), ps.getIntPref(RESP_TIMEOUT))
				.isComplete();
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	@Override
	protected String getTitle(MainActivityDelegate a) {
		return a.getString(R.string.add_xtream_source);
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
