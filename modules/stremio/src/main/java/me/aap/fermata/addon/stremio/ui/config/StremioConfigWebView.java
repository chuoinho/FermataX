package me.aap.fermata.addon.stremio.ui.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebStorage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;

/** Dedicated provider configuration WebView with native mobile/AA input behavior. */
public final class StremioConfigWebView extends WebView {
	private static final long INPUT_PROBE_DELAY_MILLIS = 250L;
	private static final long COMPLETION_PROBE_TIMEOUT_MILLIS = 500L;
	private static final long ORIGIN_CLEANUP_TIMEOUT_MILLIS = 1_500L;
	private StremioConfigWebController controller;
	private PendingInput input;
	private long inputSequence;
	private long pageGeneration;
	private long gestureSequence;
	private float touchDownX;
	private float touchDownY;
	private boolean touchMoved;
	private boolean explicitActionCandidate;

	public StremioConfigWebView(Context context) {
		super(context);
	}

	public StremioConfigWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public StremioConfigWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	void bind(StremioConfigWebController controller) {
		this.controller = controller;
	}

	CookieManager configCookies() {
		return controller.cookies();
	}

	WebStorage configStorage() {
		return controller.storage();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		boolean explicit = false;
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				long sequence = ++gestureSequence;
				touchDownX = event.getX();
				touchDownY = event.getY();
				touchMoved = false;
				explicitActionCandidate = false;
				classifyAction(sequence, event.getX(), event.getY());
			}
			case MotionEvent.ACTION_MOVE -> {
				int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
				if ((Math.abs(event.getX() - touchDownX) > slop) ||
						(Math.abs(event.getY() - touchDownY) > slop)) touchMoved = true;
			}
			case MotionEvent.ACTION_UP -> {
				explicit = !touchMoved && (explicitActionCandidate || isAnchorHit());
				if (explicit && (controller != null)) {
					controller.noteExplicitCompletionGesture();
				}
			}
			case MotionEvent.ACTION_CANCEL -> {
				touchMoved = true;
				explicitActionCandidate = false;
			}
		}

		boolean handled = super.onTouchEvent(event);
		if ((event.getActionMasked() == MotionEvent.ACTION_UP) && !explicit) {
			postDelayed(this::requestProjectedInput, INPUT_PROBE_DELAY_MILLIS);
		}
		return handled;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		InputConnection target = super.onCreateInputConnection(outAttrs);
		if (target == null) return null;
		return new InputConnectionWrapper(target, false) {
			@Override
			public boolean performEditorAction(int actionCode) {
				if (isSubmitAction(actionCode)) noteImeSubmit();
				return super.performEditorAction(actionCode);
			}

			@Override
			public boolean sendKeyEvent(KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
						(event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) noteImeSubmit();
				return super.sendKeyEvent(event);
			}
		};
	}

	@Override
	public void destroy() {
		cancelPendingInput();
		controller = null;
		super.destroy();
	}

	void pageChanged(String url) {
		pageGeneration++;
		cancelPendingInput();
	}

	void verifyExplicitCompletionNavigation(String url) {
		StremioConfigWebController current = controller;
		if (current == null) return;
		long sequence = gestureSequence;
		AtomicBoolean completed = new AtomicBoolean();
		java.util.function.Consumer<Boolean> finish = explicit -> {
			if (!completed.compareAndSet(false, true)) return;
			StremioConfigWebController active = controller;
			if (active == null) return;
			if (explicit) active.noteExplicitCompletionGesture();
			active.handleNavigation(url);
		};
		postDelayed(() -> finish.accept(false), COMPLETION_PROBE_TIMEOUT_MILLIS);
		if (!touchMoved && (explicitActionCandidate || isAnchorHit())) {
			finish.accept(true);
			return;
		}
		evaluateJavascript(actionClassifierScript(touchDownX, touchDownY), raw ->
				finish.accept((sequence == gestureSequence) && !touchMoved &&
						"true".equals(raw)));
	}

	@SuppressLint("AddJavascriptInterface")
	void clearOriginStorage(String expectedOrigin, Runnable completed) {
		String bridgeName = "FermataXCleanup" + UUID.randomUUID().toString().replace("-", "");
		String token = UUID.randomUUID().toString();
		OriginCleanupSession session = new OriginCleanupSession(
				this, bridgeName, token, completed);
		addJavascriptInterface(session.bridge, bridgeName);
		postDelayed(session.timeout, ORIGIN_CLEANUP_TIMEOUT_MILLIS);
		try {
			evaluateJavascript(cleanupScript(expectedOrigin, bridgeName, token), null);
		} catch (Throwable ignored) {
			session.finish();
		}
	}

	private void classifyAction(long sequence, float x, float y) {
		evaluateJavascript(actionClassifierScript(x, y), raw -> {
			if (sequence != gestureSequence) return;
			explicitActionCandidate = "true".equals(raw);
		});
	}

	private boolean isAnchorHit() {
		int type = getHitTestResult().getType();
		return (type == HitTestResult.SRC_ANCHOR_TYPE) ||
				(type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE);
	}

	private void requestProjectedInput() {
		if ((controller == null) || (input != null)) return;
		long sequence = ++inputSequence;
		long generation = pageGeneration;
		String domToken = "fx-" + UUID.randomUUID();
		evaluateJavascript(captureInputScript(domToken), raw ->
				openProjectedInput(sequence, generation, domToken, raw));
	}

	private void openProjectedInput(long sequence, long generation, String domToken, String raw) {
		if ((controller == null) || (input != null) || (sequence != inputSequence) ||
				(generation != pageGeneration) || (raw == null) || "null".equals(raw)) return;
		try {
			String json = new JSONArray('[' + raw + ']').getString(0);
			JSONObject value = new JSONObject(json);
			String pageUrl = value.getString("page");
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(activity -> {
				if ((controller == null) || (input != null) || (sequence != inputSequence) ||
						(generation != pageGeneration)) return;
				FutureSupplier<String> request = activity.getAppActivity().requestTextInput(
						value.optString("title", ""), value.optString("value", ""),
						inputType(value.optString("type", "text")));
				if (request == null) {
					removeInputToken(domToken, pageUrl);
					return;
				}
				PendingInput pending = new PendingInput(request, domToken, pageUrl, generation);
				input = pending;
				request.onSuccess(text -> applyProjectedInput(pending, text)).onFailure(error -> {
					if (input != pending) return;
					input = null;
					removeInputToken(pending.domToken, pending.pageUrl);
				});
			});
		} catch (Exception ignored) {
			removeInputToken(domToken, null);
		}
	}

	private void applyProjectedInput(PendingInput pending, String text) {
		if (input != pending) return;
		input = null;
		if ((controller == null) || (pending.pageGeneration != pageGeneration)) {
			removeInputToken(pending.domToken, pending.pageUrl);
			return;
		}
		evaluateJavascript(applyInputScript(pending.domToken, pending.pageUrl, text), raw -> {
			if (!"\"applied\"".equals(raw) || (controller == null) ||
					(pending.pageGeneration != pageGeneration)) return;
			controller.noteExplicitCompletionGesture();
			evaluateJavascript(submitInputScript(pending.domToken, pending.pageUrl), ignored ->
					removeInputToken(pending.domToken, pending.pageUrl));
		});
	}

	private void removeInputToken(String token, String pageUrl) {
		if (controller == null) return;
		evaluateJavascript(removeInputTokenScript(token, pageUrl), null);
	}

	private void cancelPendingInput() {
		inputSequence++;
		PendingInput pending = input;
		input = null;
		if (pending == null) return;
		pending.request.cancel();
		removeInputToken(pending.domToken, pending.pageUrl);
	}

	private void noteImeSubmit() {
		StremioConfigWebController current = controller;
		if (current != null) current.noteExplicitCompletionGesture();
	}

	static boolean isSubmitAction(int actionCode) {
		return (actionCode == EditorInfo.IME_ACTION_DONE) ||
				(actionCode == EditorInfo.IME_ACTION_GO) ||
				(actionCode == EditorInfo.IME_ACTION_SEARCH) ||
				(actionCode == EditorInfo.IME_ACTION_SEND);
	}

	static String actionClassifierScript(float x, float y) {
		return "(function(){var e=document.elementFromPoint(" + x + ',' + y + ");" +
				"while(e&&e!==document.documentElement){var t=(e.tagName||'').toLowerCase();" +
				"var r=(e.getAttribute&&e.getAttribute('role')||'').toLowerCase();" +
				"var y=(e.type||'').toLowerCase();if((t==='a'&&e.href)||t==='button'||" +
				"(t==='input'&&(y==='submit'||y==='button'||y==='image'))||" +
				"r==='button'||r==='link')return true;e=e.parentElement;}return false;})()";
	}

	static String captureInputScript(String token) {
		return "(function(){var e=document.activeElement;if(!e||" +
				"(!(e instanceof HTMLInputElement)&&!(e instanceof HTMLTextAreaElement)&&" +
				"!e.isContentEditable))return null;var t=" + JSONObject.quote(token) + ';' +
				"e.setAttribute('data-fermatax-input-token',t);return JSON.stringify({value:" +
				"(e.isContentEditable?e.innerText:e.value)||'',type:(e.type||'text')," +
				"title:(e.getAttribute('aria-label')||e.placeholder||e.name||'')," +
				"page:location.href});})()";
	}

	static String applyInputScript(String token, String pageUrl, String text) {
		return "(function(){if(location.href!==" + JSONObject.quote(pageUrl) +
				")return 'stale-page';var e=document.querySelector('[data-fermatax-input-token=\"'+" +
				JSONObject.quote(token) + "+'\"]');if(!e||document.activeElement!==e)" +
				"return 'stale-focus';var v=" + JSONObject.quote(text == null ? "" : text) + ';' +
				"if(e.isContentEditable)e.innerText=v;else e.value=v;" +
				"e.dispatchEvent(new InputEvent('input',{bubbles:true,data:v,inputType:'insertText'}));" +
				"e.dispatchEvent(new Event('change',{bubbles:true}));return 'applied';})()";
	}

	static String submitInputScript(String token, String pageUrl) {
		return "(function(){if(location.href!==" + JSONObject.quote(pageUrl) +
				")return 'stale-page';var e=document.querySelector('[data-fermatax-input-token=\"'+" +
				JSONObject.quote(token) + "+'\"]');if(!e||document.activeElement!==e)" +
				"return 'stale-focus';e.removeAttribute('data-fermatax-input-token');" +
				"if(e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}" +
				"else{e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter'," +
				"keyCode:13,bubbles:true}));e.dispatchEvent(new KeyboardEvent('keyup'," +
				"{key:'Enter',code:'Enter',keyCode:13,bubbles:true}));}return 'submitted';})()";
	}

	static String removeInputTokenScript(String token, String pageUrl) {
		String pageCheck = (pageUrl == null) ? "" :
				"if(location.href!==" + JSONObject.quote(pageUrl) + ")return;";
		return "(function(){" + pageCheck +
				"var e=document.querySelector('[data-fermatax-input-token=\"'+" +
				JSONObject.quote(token) + "+'\"]');if(e)e.removeAttribute(" +
				"'data-fermatax-input-token');})()";
	}

	static String cleanupScript(String expectedOrigin, String bridgeName, String token) {
		return "(async function(){try{if(location.origin===" + JSONObject.quote(expectedOrigin) +
				"){try{localStorage.clear();sessionStorage.clear();}catch(e){}" +
				"try{var p=location.pathname||'/';var ps=['/'];var s=p.split('/').filter(Boolean);" +
				"var c='';for(var i=0;i<s.length;i++){c+='/'+s[i];ps.push(c);}" +
				"document.cookie.split(';').forEach(function(v){var n=v.split('=')[0].trim();" +
				"if(n)ps.forEach(function(x){document.cookie=n+'=; Max-Age=0; Path='+x;});});}catch(e){}" +
				"var jobs=[];try{if(indexedDB.databases)jobs.push(indexedDB.databases().then(function(ds){" +
				"return Promise.all(ds.filter(function(d){return d.name;}).map(function(d){" +
				"return new Promise(function(r){var q=indexedDB.deleteDatabase(d.name);" +
				"q.onsuccess=q.onerror=q.onblocked=function(){r();};});}));}));}catch(e){}" +
				"try{if(window.caches)jobs.push(caches.keys().then(function(ks){return Promise.all(" +
				"ks.map(function(k){return caches.delete(k);}));}));}catch(e){}" +
				"try{if(navigator.serviceWorker)jobs.push(navigator.serviceWorker.getRegistrations()" +
				".then(function(rs){return Promise.all(rs.map(function(r){return r.unregister();}));}));}" +
				"catch(e){}await Promise.race([Promise.allSettled(jobs),new Promise(function(r){" +
				"setTimeout(r,1000);})]);}}catch(e){}finally{try{window[" +
				JSONObject.quote(bridgeName) + "].complete(" + JSONObject.quote(token) + ");}catch(e){}}})()";
	}

	private static int inputType(String type) {
		return switch (type.toLowerCase(Locale.ROOT)) {
			case "password" -> InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_PASSWORD;
			case "email" -> InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
			case "url" -> InputType.TYPE_CLASS_TEXT |
					InputType.TYPE_TEXT_VARIATION_URI;
			case "number" -> InputType.TYPE_CLASS_NUMBER;
			default -> InputType.TYPE_CLASS_TEXT;
		};
	}

	private record PendingInput(FutureSupplier<String> request, String domToken,
			String pageUrl, long pageGeneration) {
	}

	private static final class OriginCleanupSession {
		private final StremioConfigWebView view;
		private final String bridgeName;
		private final AtomicBoolean finished = new AtomicBoolean();
		private final Runnable completed;
		private final CleanupBridge bridge;
		private final Runnable timeout = this::finish;

		private OriginCleanupSession(StremioConfigWebView view, String bridgeName,
				String token, Runnable completed) {
			this.view = view;
			this.bridgeName = bridgeName;
			this.completed = completed;
			bridge = new CleanupBridge(token, () -> view.post(this::finish));
		}

		private void finish() {
			if (!finished.compareAndSet(false, true)) return;
			view.removeCallbacks(timeout);
			view.removeJavascriptInterface(bridgeName);
			completed.run();
		}
	}

	public static final class CleanupBridge {
		private final String token;
		private final Runnable completed;

		private CleanupBridge(String token, Runnable completed) {
			this.token = token;
			this.completed = completed;
		}

		@JavascriptInterface
		public void complete(String suppliedToken) {
			if (token.equals(suppliedToken)) completed.run();
		}
	}
}
