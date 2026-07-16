package me.aap.fermata.addon.web;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;
import static android.view.MotionEvent.ACTION_UP;
import static androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING;
import static androidx.webkit.WebViewFeature.FORCE_DARK;
import static androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EDIT;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EVENT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.TextChangedListener;
import me.aap.utils.ui.view.ToolBarView;
import org.json.JSONObject;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebView extends WebView
		implements TextChangedListener, TextView.OnEditorActionListener, PreferenceStore.Listener,
		MainActivityListener {
	private final boolean isCar;
	private WebBrowserAddon addon;
	private FermataWebClient webClient;
	private FermataChromeClient chrome;
	private String lastUrl;
	private long cookieFlushStamp;

	public FermataWebView(Context context) {
		this(context, null);
	}

	public FermataWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	public FermataWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
									 FermataChromeClient chromeClient) {
		this.addon = addon;
		this.webClient = webClient;
		setWebViewClient(webClient);
		setWebChromeClient(chromeClient);
		WebSettings s = getSettings();
		s.setSupportZoom(true);
		s.setBuiltInZoomControls(true);
		s.setDisplayZoomControls(false);
		s.setDatabaseEnabled(true);
		s.setDomStorageEnabled(true);
		s.setAllowFileAccess(true);
		s.setLoadWithOverviewMode(true);
		s.setJavaScriptEnabled(true);
		s.setMediaPlaybackRequiresUserGesture(false);
		s.setJavaScriptCanOpenWindowsAutomatically(true);

		addJavascriptInterface(createJsInterface(), FermataJsInterface.NAME);
		CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

		addon.getPreferenceStore().addBroadcastListener(this);
		getActivity().onSuccess(a -> a.addBroadcastListener(this));

		setDesktopMode(addon, false);
		setForceDark(addon, false);
	}

	@Override
	public void loadUrl(@NonNull String url) {
		if (!isScriptUrl(url)) lastUrl = url;
		super.loadUrl(url);
	}

	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		if (!BuildConfig.AUTO) super.onWindowVisibilityChanged(visibility);
		else if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		if (prefs.contains(a.getDesktopVersionPref())) {
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentPref())) {
			UserAgent.ua = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentDesktopPref())) {
			UserAgent.uaDesktop = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getForceDarkPref())) {
			setForceDark(addon, true);
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			getAddon().getPreferenceStore().removeBroadcastListener(this);
		}
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		FermataChromeClient c = getWebChromeClient();
		if ((c != null) && c.isFullScreen()) getActivity().onSuccess(a -> c.setFullScreen(a, true));
	}

	private void setDesktopMode(WebBrowserAddon a, boolean reload) {
		if (getClass() != FermataWebView.class) return;

		WebSettings s = getSettings();
		boolean v = a.getPreferenceStore().getBooleanPref(a.getDesktopVersionPref());
		String ua = v ? UserAgent.getUaDesktop(s, a) : UserAgent.getUa(s, a);
		s.setUseWideViewPort(v);

		try {
			Log.d("Setting User-Agent to " + ua);
			s.setUserAgentString(ua);
		} catch (Exception ex) {
			Log.e(ex, "Invalid User-Agent: ", ua);
			String msg = ex.getLocalizedMessage();
			if (msg == null) msg = "Invalid User-Agent: " + ua;
			UiUtils.showAlert(getContext(), msg);
		}

		if (reload) reload();
	}

	@SuppressWarnings("deprecation")
	private void setForceDark(WebBrowserAddon a, boolean reload) {
		if ((VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) &&
				(WebViewFeature.isFeatureSupported(ALGORITHMIC_DARKENING))) {
			boolean dark = a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark());
			WebSettingsCompat.setAlgorithmicDarkeningAllowed(getSettings(), dark);
			if (reload) reload();
		} else if (WebViewFeature.isFeatureSupported(FORCE_DARK)) {
			int force;
			int strategy;
			if (a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark())) {
				force = WebSettingsCompat.FORCE_DARK_ON;
				strategy = WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING;
			} else {
				force = WebSettingsCompat.FORCE_DARK_OFF;
				strategy = WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY;
			}
			WebSettingsCompat.setForceDark(getSettings(), force);
			if (WebViewFeature.isFeatureSupported(FORCE_DARK_STRATEGY))
				WebSettingsCompat.setForceDarkStrategy(getSettings(), strategy);
			if (reload) reload();
		}
	}

	private boolean isDarkPhoneTheme() {
		int mode = getResources().getConfiguration().uiMode;
		return (mode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
	}

	protected FermataJsInterface createJsInterface() {
		return new FermataJsInterface(this);
	}

	protected boolean isCar() {
		return BuildConfig.AUTO && isCar;
	}

	public WebBrowserAddon getAddon() {
		return addon;
	}

	@NonNull
	@Override
	public FermataWebClient getWebViewClient() {
		return webClient;
	}

	public void setWebChromeClient(FermataChromeClient chrome) {
		this.chrome = chrome;
		super.setWebChromeClient(chrome);
	}

	@Nullable
	@Override
	public FermataChromeClient getWebChromeClient() {
		return chrome;
	}

	public boolean exitFullScreenForBack() {
		FermataChromeClient c = getWebChromeClient();
		if ((c == null) || !c.isFullScreen()) return false;
		onUserExitFullScreen();
		c.exitFullScreen();
		return true;
	}

	protected void onUserExitFullScreen() {
	}

	protected void pageLoaded(String uri) {
		addFocusHighlight();
		lastUrl = uri;
		getAddon().setLastUrl(uri);
		getActivity().onSuccess(a -> {
			ActivityFragment f = a.getActiveFragment();
			if (f == null) return;

			ToolBarView.Mediator m = f.getToolBarMediator();

			if (m instanceof WebToolBarMediator wm) {
				ToolBarView tb = a.getToolBar();
				wm.setAddress(tb, uri);
				wm.setButtonsVisibility(tb, canGoBack(), canGoForward());
			}

			flushCookiesSoon();
		});
	}

	protected void addFocusHighlight() {
		evaluateJavascript("""
				(function() {
				  if (document.getElementById('fermata-focus-style')) return;
				  var style = document.createElement('style');
				  style.id = 'fermata-focus-style';
				  style.innerHTML = ':focus {outline: 2px solid blue !important; border-radius: 5px;}';
				  (document.head || document.documentElement).appendChild(style);
				})()""", null);
	}

	protected void flushCookiesSoon() {
		long stamp = ++cookieFlushStamp;
		postDelayed(() -> {
			if (stamp == cookieFlushStamp) CookieManager.getInstance().flush();
		}, 750);
	}

	boolean recoverRenderProcess() {
		Log.e("WebView renderer process is gone. Recreating WebView.");

		if (!(getParent() instanceof ViewGroup parent)) {
			destroy();
			return true;
		}

		int index = parent.indexOfChild(this);
		ViewGroup.LayoutParams lp = getLayoutParams();
		int id = getId();
		int visibility = getVisibility();
		String url = getRecoveryUrl();

		try {
			FermataChromeClient oldChrome = getWebChromeClient();
			if ((oldChrome != null) && oldChrome.isFullScreen()) oldChrome.exitFullScreen();

			FermataWebView web = getClass().getConstructor(Context.class).newInstance(getContext());
			web.setId(id);
			web.setVisibility(visibility);
			web.setLayoutParams(lp);

			FermataWebClient client = (webClient == null) ? new FermataWebClient() : webClient.createReplacement();
			FermataChromeClient chromeClient = (oldChrome == null) ? null : oldChrome.createReplacement(web);

			if ((addon == null) || (chromeClient == null)) {
				parent.removeView(this);
				destroy();
				return true;
			}

			web.init(addon, client, chromeClient);
			parent.removeView(this);
			destroy();
			parent.addView(web, index, lp);
			if ((url != null) && !url.isEmpty()) web.loadUrl(url);
		} catch (Throwable ex) {
			Log.e(ex, "Failed to recreate WebView after renderer process loss");
			parent.removeView(this);
			destroy();
		}

		return true;
	}

	protected String getRecoveryUrl() {
		String current = getUrl();
		if ((current != null) && !isScriptUrl(current)) return current;
		if ((lastUrl != null) && !isScriptUrl(lastUrl)) return lastUrl;
		WebBrowserAddon a = getAddon();
		return selectRecoveryUrl(current, lastUrl, (a == null) ? null : a.getLastUrl());
	}

	static String selectRecoveryUrl(String currentUrl, String lastUrl, String addonLastUrl) {
		String url = currentUrl;
		if ((url == null) || isScriptUrl(url)) url = lastUrl;
		if ((url == null) || isScriptUrl(url)) url = addonLastUrl;
		return ((url == null) || isScriptUrl(url)) ? null : url;
	}

	static boolean isScriptUrl(String url) {
		return (url != null) && url.regionMatches(true, 0, "javascript:", 0, 11);
	}

	protected boolean requestFullScreen() {
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isCar()) {
			if (event.getAction() == ACTION_UP) checkTextInput();
		}
		return super.onTouchEvent(event);
	}

	private void checkTextInput() {
		if (!BuildConfig.AUTO || isKeyboardActive()) return;

		Log.d("checkTextInput");
		loadUrl("javascript:\n" + "function checkInput() {\n" + "  var e =  document.activeElement;" +
				"\n" + "  if (e == null) return;\n" + "  if (e instanceof HTMLInputElement) {\n" + "    " +
				JS_EVENT + '(' + JS_EDIT + ", e.value);\n" +
				"  } else if(e.getAttribute('contenteditable') == 'true') {\n" + "    " + JS_EVENT + '(' +
				JS_EDIT + ", e.innerText);\n" + "  }\n" + "}\n" + "setTimeout(checkInput, 500);");
	}

	private void setTextInput(CharSequence text) {
		if (!BuildConfig.AUTO) return;

		Log.d(text);
		evaluateJavascript("var e = document.activeElement;\n" +
				"var text = " + JSONObject.quote(text == null ? "" : text.toString()) + ";\n" +
				"if (e != null) {\n" +
				"  if (e.isContentEditable) e.innerText = text;\n" +
				"  else e.value = text;\n" +
				"  e.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));\n" +
				"  e.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true }));\n" +
				"  e.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: 'insertText' }));\n" +
				"  e.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));\n" +
				"  e.dispatchEvent(new Event('change', { bubbles: true }));\n" +
				"}", null);
	}


	protected void submitForm() {
		if (!BuildConfig.AUTO) return;
		loadUrl("""
				javascript:
				var ae = document.activeElement;
				if (ae.form != null) {
				  ae.form.submit();
				} else {
				  var e = new KeyboardEvent('keydown',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				  e = new KeyboardEvent('keyup',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				}""");
	}

	public void showKeyboard(String text) {
		if (!BuildConfig.AUTO) return;

		getActivity().onSuccess(a -> {
			EditText et = a.getAppActivity().startInput(this);
			if (et == null) return;

			if (text != null) {
				et.setText(text);
				et.setSelection(et.getText().length());
			}

			et.setOnEditorActionListener(this);
		});
	}

	public void hideKeyboard() {
		if (!BuildConfig.AUTO) return;
		getActivity().onSuccess(a -> a.getAppActivity().stopInput());
	}

	private boolean isKeyboardActive() {
		if (!BuildConfig.AUTO) return false;

		FermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();
		return (a != null) && a.isInputActive();
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (BuildConfig.AUTO) setTextInput(s);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, @Nullable KeyEvent event) {
		if (!BuildConfig.AUTO) return false;

		switch (actionId) {
			case EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
					EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
				submitForm();
				hideKeyboard();
			}
		}

		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		FermataChromeClient chrome = getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.onTouchEvent(this, ev);
		} else if (BuildConfig.AUTO) {
			FermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();

			if ((a != null) && a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	static final class UserAgent {
		private static final Pattern pattern =
				Pattern.compile(".+ AppleWebKit/(\\S+) .+ Chrome/(\\S+) .+");
		static String ua;
		static String uaDesktop;

		static String getUa(WebSettings s, WebBrowserAddon a) {
			if (ua != null) return ua;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String av;
				if (VERSION.SDK_INT >= VERSION_CODES.R) av = VERSION.RELEASE_OR_CODENAME;
				else av = VERSION.RELEASE;
				String wv = m.group(1);
				String cv = m.group(2);
				UserAgent.ua = a.getUserAgent().replace("{ANDROID_VERSION}", av)
						.replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
				UserAgent.ua = normalize(UserAgent.ua);
				if (UserAgent.ua.isEmpty()) UserAgent.ua = ua;
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				UserAgent.ua = ua;
			}

			return UserAgent.ua;
		}

		static String getUaDesktop(WebSettings s, WebBrowserAddon a) {
			if (uaDesktop != null) return uaDesktop;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String wv = m.group(1);
				String cv = m.group(2);
				uaDesktop = a.getUserAgentDesktop().replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				int i1 = ua.indexOf('(') + 1;
				int i2 = ua.indexOf(')', i1);
				uaDesktop = ua.substring(0, i1) + "X11; Linux x86_64" +
						ua.substring(i2).replace(" Mobile ", " ").replaceFirst(" Version/\\d+\\.\\d+ ", " ");
			}

			return uaDesktop = normalize(uaDesktop);
		}

		private static String normalize(String ua) {
			try (SharedTextBuilder b = SharedTextBuilder.get()) {
				int cut = 0;
				boolean changed = false;

				for (int i = 0, n = ua.length(); i < n; i++) {
					char c = ua.charAt(i);

					if (c <= ' ') {
						if ((b.length() == 0) || (ua.charAt(i - 1) == ' ')) {
							changed = true;
							continue;
						} else if (c != ' ') {
							b.append(' ');
							changed = true;
							continue;
						}
					}

					b.append(c);
				}

				for (int i = b.length() - 1; i >= 0; i--) {
					if (b.charAt(i) == ' ') cut++;
					else break;
				}

				if (cut != 0) {
					changed = true;
					b.setLength(b.length() - cut);
				}

				return changed ? b.toString() : ua;
			}
		}
	}
}
