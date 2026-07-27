package me.aap.fermata.addon.stremio.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class StremioConfigWebViewTest {
	@Test
	public void classifierIncludesOnlyExplicitCompletionControls() {
		String script = StremioConfigWebView.actionClassifierScript(10, 20);

		assertTrue(script.contains("t==='a'&&e.href"));
		assertTrue(script.contains("t==='button'"));
		assertTrue(script.contains("y==='submit'"));
		assertTrue(script.contains("y==='button'"));
		assertTrue(script.contains("y==='image'"));
		assertTrue(script.contains("r==='button'"));
		assertTrue(script.contains("r==='link'"));
		assertFalse(script.contains("y==='text'"));
		assertFalse(script.contains("textarea"));
	}

	@Test
	public void plainTapAndScrollDoNotArmCompletion() {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/config", false, java.util.Map.of(), callback);
		long now = 1_000L;

		dispatch(view, now, MotionEvent.ACTION_DOWN, 10, 10);
		dispatch(view, now + 10, MotionEvent.ACTION_UP, 10, 10);
		dispatch(view, now + 20, MotionEvent.ACTION_DOWN, 10, 10);
		dispatch(view, now + 30, MotionEvent.ACTION_MOVE, 100, 100);
		dispatch(view, now + 40, MotionEvent.ACTION_UP, 100, 100);

		assertTrue(controller.handleNavigation(
				"https://provider.example.invalid/token/manifest.json"));
		assertEquals(0, callback.configured);
		assertEquals(1, callback.failures);
		controller.close();
	}

	@Test
	public void projectedInputScriptsBindPageTokenAndExactFocus() {
		String page = "https://provider.example.invalid/config?step=2";
		String token = "fx-test-token";
		String capture = StremioConfigWebView.captureInputScript(token);
		String apply = StremioConfigWebView.applyInputScript(token, page, "typed value");
		String submit = StremioConfigWebView.submitInputScript(token, page);

		assertTrue(capture.contains("data-fermatax-input-token"));
		assertTrue(capture.contains("page:location.href"));
		assertTrue(apply.contains("location.href!==" + JSONObject.quote(page)));
		assertTrue(apply.contains(token));
		assertTrue(apply.contains("document.activeElement!==e"));
		assertTrue(apply.contains("return 'stale-page'"));
		assertTrue(apply.contains("return 'stale-focus'"));
		assertTrue(submit.contains("location.href!==" + JSONObject.quote(page)));
		assertTrue(submit.contains(token));
		assertTrue(submit.contains("document.activeElement!==e"));
		assertTrue(submit.contains("requestSubmit"));
	}

	@Test
	public void projectedFieldApplicationDoesNotImplicitlySubmitForm() {
		String apply = StremioConfigWebView.applyInputScript(
				"fx-test-token", "https://provider.example.invalid/config", "username");

		assertFalse(apply.contains("requestSubmit"));
		assertFalse(apply.contains("form.submit"));
		assertTrue(apply.contains("return 'applied'"));
	}

	@Test
	public void directCompletionWaitsForExplicitDomClassification() {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/config", false, java.util.Map.of(), callback);

		view.verifyExplicitCompletionNavigation(
				"https://provider.example.invalid/token/manifest.json");
		Shadows.shadowOf((WebView) view).getLastEvaluatedJavascriptCallback()
				.onReceiveValue("true");

		assertEquals(1, callback.configured);
		assertEquals(0, callback.failures);
		controller.close();
	}

	@Test
	public void cleanupScriptIsOriginBoundAndClearsOriginOwnedStores() {
		String script = StremioConfigWebView.cleanupScript(
				"https://provider.example.invalid", "CleanupBridge", "opaque-token");

		assertTrue(script.contains("location.origin===" +
				JSONObject.quote("https://provider.example.invalid")));
		assertTrue(script.contains("localStorage.clear()"));
		assertTrue(script.contains("sessionStorage.clear()"));
		assertTrue(script.contains("indexedDB.deleteDatabase"));
		assertTrue(script.contains("caches.delete"));
		assertTrue(script.contains("serviceWorker.getRegistrations"));
		assertTrue(script.contains("CleanupBridge"));
		assertTrue(script.contains("opaque-token"));
		assertFalse(script.contains("removeAllCookies"));
		assertFalse(script.contains("clearCache"));
	}

	@Test
	public void postFormsRenderActionableErrorWithoutRequestData() throws Exception {
		WebResourceResponse response = StremioConfigWebClient.postFormErrorResponse();
		String html = new String(response.getData().readAllBytes(), StandardCharsets.UTF_8);

		assertEquals(200, response.getStatusCode());
		assertEquals("text/html", response.getMimeType());
		assertTrue(html.contains("Form could not be submitted"));
		assertTrue(html.contains("history.back()"));
		assertTrue(html.contains("Go back"));
		assertFalse(html.contains("manifest.json"));
		assertFalse(html.contains("password"));
		assertFalse(html.contains("token="));
	}

	@Test
	public void submitImeActionsAreExplicitButNavigationActionsAreNot() {
		assertTrue(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_DONE));
		assertTrue(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_GO));
		assertTrue(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_SEARCH));
		assertTrue(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_SEND));
		assertFalse(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_NEXT));
		assertFalse(StremioConfigWebView.isSubmitAction(EditorInfo.IME_ACTION_NONE));
	}

	@Test
	public void originCookiePathsStayWithinConfiguredProvider() {
		assertEquals("https://provider.example.invalid:8443",
				StremioConfigOriginCleaner.origin(
						"https://provider.example.invalid:8443/config/account"));
		assertEquals(java.util.List.of("/", "/config/account", "/config"),
				StremioConfigOriginCleaner.cookiePaths("/config/account"));
	}

	private static void dispatch(StremioConfigWebView view, long time, int action,
			float x, float y) {
		MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
		try {
			view.onTouchEvent(event);
		} finally {
			event.recycle();
		}
	}

	private static final class RecordingCallback implements StremioConfigCallback {
		private int configured;
		private int failures;

		@Override
		public void onConfigured(StremioConfigResult result) {
			configured++;
		}

		@Override
		public void onFailure(Failure failure) {
			failures++;
		}
	}
}
