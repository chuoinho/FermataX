package me.aap.fermata.addon.stremio.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import me.aap.fermata.addon.stremio.net.NetworkConsent;

import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class StremioConfigWebControllerTest {
	@Test
	public void moduleLayoutDeclaresDedicatedWebView() throws Exception {
		Path layout = Paths.get("src/main/res/layout/stremio_config_web.xml");
		if (!Files.isRegularFile(layout)) {
			layout = Paths.get("modules/stremio/src/main/res/layout/stremio_config_web.xml");
		}
		String xml = new String(Files.readAllBytes(layout), StandardCharsets.UTF_8);

		assertTrue(xml.contains("me.aap.fermata.addon.stremio.ui.config.StremioConfigWebView"));
		assertTrue(xml.contains("@+id/stremio_config_web"));
	}

	@Test
	public void appliesLockedDownWebViewSettingsWithoutJavascriptBridge() {
		Session session = session();
		WebSettings settings = session.view.getSettings();

		assertTrue(settings.getJavaScriptEnabled());
		assertTrue(settings.getDomStorageEnabled());
		assertEquals(WebSettings.LOAD_NO_CACHE, settings.getCacheMode());
		assertFalse(settings.getAllowFileAccess());
		assertFalse(settings.getAllowContentAccess());
		assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
		assertFalse(settings.supportMultipleWindows());
		assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.getMixedContentMode());
		assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(session.view));
		assertTrue(session.view.isFocusableInTouchMode());
		session.controller.close();
	}

	@Test
	public void reportsFinalUrlOnceAndIgnoresCallbacksAfterClose() {
		Session session = session();
		String finalUrl = "https://provider.example.invalid/token/manifest.json?key=secret";

		assertFalse(session.controller.handleNavigation("https://provider.example.invalid/config/step2"));
		session.controller.noteExplicitCompletionGesture();
		assertTrue(session.controller.handleNavigation(finalUrl));
		assertTrue(session.controller.handleNavigation(finalUrl));
		assertEquals(1, session.callback.configured.get());
		assertEquals(0, session.callback.failures.get());

		session.controller.close();
		assertTrue(session.controller.isClosed());
		assertTrue(session.controller.handleNavigation("https://evil.example.invalid/manifest.json"));
		assertEquals(0, session.callback.failures.get());
	}

	@Test
	public void blockedNavigationTerminatesSessionWithoutLeakingUrl() {
		Session session = session();

		assertTrue(session.controller.handleNavigation(
				"intent://evil.example/private-token#Intent;scheme=https;end"));

		assertEquals(0, session.callback.configured.get());
		assertEquals(1, session.callback.failures.get());
		assertEquals(StremioConfigCallback.Failure.BLOCKED_NAVIGATION,
				session.callback.lastFailure);
		session.controller.close();
	}

	@Test
	public void nonInteractiveManifestNavigationIsRejected() {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/private-token/manifest.json", false, Map.of(), callback);

		controller.start();

		assertEquals(0, callback.configured.get());
		assertEquals(1, callback.failures.get());
		controller.close();
	}

	@Test
	public void launchUsesOpaqueTokenHeaderAndNeverRendersIt() {
		String token = "fixture-private-token";
		StremioConfigLaunch launch = new StremioConfigLaunch(new StremioSourceSecret(
				"https://provider.example.invalid/manifest.json?discarded=true", token), false);

		assertEquals("https://provider.example.invalid/configure", launch.initialUrlForTest());
		assertEquals(token, launch.initialHeadersForTest()
				.get("X-Stremio-Configuration-Token"));
		assertFalse(launch.toString().contains(token));
		assertFalse(launch.toString().contains("discarded"));
	}

	@Test
	public void finalManifestRequiresClassifiedExplicitGesture() {
		Session redirected = session();
		assertTrue(redirected.controller.handleNavigation(
				"https://provider.example.invalid/private/manifest.json"));
		assertEquals(0, redirected.callback.configured.get());
		assertEquals(1, redirected.callback.failures.get());
		redirected.controller.close();

		Session clicked = session();
		clicked.controller.noteExplicitCompletionGesture();
		assertTrue(clicked.controller.handleNavigation(
				"https://provider.example.invalid/private/manifest.json"));
		assertEquals(1, clicked.callback.configured.get());
		assertEquals(0, clicked.callback.failures.get());
		clicked.controller.close();
	}

	@Test
	public void userActivationSurvivesJavascriptRedirectButCannotBeForgedByPageLoad() {
		Session session = session();
		session.controller.noteExplicitCompletionGesture();

		assertTrue(session.controller.handleNavigation(
				"https://provider.example.invalid/final/manifest.json"));
		assertEquals(1, session.callback.configured.get());
		assertEquals(0, session.callback.failures.get());
		session.controller.close();
	}

	@Test
	public void boundedLoaderReceivesTokenOnlyForInitialRequest() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		java.util.concurrent.atomic.AtomicReference<Map<String, String>> captured =
				new java.util.concurrent.atomic.AtomicReference<>();
		CookieManager.getInstance().setCookie(
				"https://provider.example.invalid/configure", "session=private");
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/configure", new NetworkConsent(false, false),
				Map.of("X-Stremio-Configuration-Token", "private"), (uri, headers) -> {
					captured.set(headers);
					return java.util.concurrent.CompletableFuture.completedFuture(
							new StremioConfigResourceLoader.Response(200, uri,
									Map.of("content-type", "text/html"),
									"<html></html>".getBytes(StandardCharsets.UTF_8)));
				}, callback);

		controller.loadResource("https://provider.example.invalid/configure", Map.of("Accept", "text/html"));
		assertEquals("private", captured.get().get("X-Stremio-Configuration-Token"));
		assertTrue(captured.get().get("Cookie").contains("session=private"));
		controller.loadResource("https://provider.example.invalid/app.js", Map.of());
		assertFalse(captured.get().containsKey("X-Stremio-Configuration-Token"));
		controller.close();
	}

	@Test
	public void crossOriginResourcesCannotReceiveProviderContextHeaders() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		java.util.concurrent.atomic.AtomicReference<Map<String, String>> captured =
				new java.util.concurrent.atomic.AtomicReference<>();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/private/configure",
				new NetworkConsent(false, false), Map.of(), (uri, headers) -> {
					captured.set(headers);
					return java.util.concurrent.CompletableFuture.completedFuture(
							new StremioConfigResourceLoader.Response(200, uri,
									Map.of("content-type", "text/javascript"), new byte[0]));
				}, callback);

		controller.loadResource("https://cdn.example.invalid/app.js", Map.of(
				"Accept", "text/javascript",
				"Origin", "https://provider.example.invalid",
				"Referer", "https://provider.example.invalid/private/configure",
				"Authorization", "Bearer private",
				"X-Stremio-Configuration-Token", "private"), false);

		assertEquals("text/javascript", captured.get().get("Accept"));
		assertFalse(captured.get().containsKey("Origin"));
		assertFalse(captured.get().containsKey("Referer"));
		assertFalse(captured.get().containsKey("Authorization"));
		assertFalse(captured.get().containsKey("X-Stremio-Configuration-Token"));
		assertFalse(captured.get().containsKey("Cookie"));
		controller.close();
	}

	@Test
	public void closeFromWorkerCancelsImmediatelyAndDestroysOnMainThread() throws Exception {
		Session session = session();
		Thread worker = new Thread(session.controller::close);

		worker.start();
		worker.join();

		assertTrue(session.controller.isClosed());
		assertTrue(session.controller.handleNavigation(
				"https://provider.example.invalid/token/manifest.json"));
		assertEquals(0, session.callback.configured.get());
		Shadows.shadowOf(Looper.getMainLooper()).idle();
	}

	@Test
	public void normalNavigationNeverArmsAnUnrelatedManifest() {
		Session session = session();

		assertFalse(session.controller.handleNavigation(
				"https://provider.example.invalid/config/next"));
		assertTrue(session.controller.handleNavigation(
				"https://provider.example.invalid/unexpected/manifest.json"));

		assertEquals(0, session.callback.configured.get());
		assertEquals(1, session.callback.failures.get());
		session.controller.close();
	}

	@Test
	public void startAndDestroyWaitForOriginCleanup() {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		DeferredOriginCleaner cleaner = new DeferredOriginCleaner();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/config", new NetworkConsent(false, false), Map.of(),
				StremioConfigResourceLoader.unavailable(), cleaner, callback);

		controller.start();
		controller.start();
		assertEquals(null, Shadows.shadowOf((WebView) view).getLastLoadedUrl());
		cleaner.finishBeforeLoad();
		assertEquals("https://provider.example.invalid/config",
				Shadows.shadowOf((WebView) view).getLastLoadedUrl());

		controller.close();
		assertFalse(Shadows.shadowOf((WebView) view).wasDestroyCalled());
		cleaner.finishBeforeDestroy();
		cleaner.finishBeforeDestroy();
		assertTrue(Shadows.shadowOf((WebView) view).wasDestroyCalled());
	}

	private static Session session() {
		Context context = RuntimeEnvironment.getApplication();
		StremioConfigWebView view = new StremioConfigWebView(context);
		RecordingCallback callback = new RecordingCallback();
		StremioConfigWebController controller = new StremioConfigWebController(view,
				"https://provider.example.invalid/config", false, Map.of(), callback);
		return new Session(view, callback, controller);
	}

	private record Session(StremioConfigWebView view, RecordingCallback callback,
			StremioConfigWebController controller) {
	}

	private static final class RecordingCallback implements StremioConfigCallback {
		private final AtomicInteger configured = new AtomicInteger();
		private final AtomicInteger failures = new AtomicInteger();
		private Failure lastFailure;

		@Override
		public void onConfigured(StremioConfigResult result) {
			configured.incrementAndGet();
		}

		@Override
		public void onFailure(Failure failure) {
			lastFailure = failure;
			failures.incrementAndGet();
		}
	}

	private static final class DeferredOriginCleaner implements StremioConfigOriginCleaner {
		private Runnable beforeLoad;
		private Runnable beforeDestroy;

		@Override
		public void clearBeforeLoad(StremioConfigWebView view, String initialUrl,
				Runnable completed) {
			beforeLoad = completed;
		}

		@Override
		public void clearBeforeDestroy(StremioConfigWebView view, String initialUrl,
				Runnable completed) {
			beforeDestroy = completed;
		}

		private void finishBeforeLoad() {
			beforeLoad.run();
		}

		private void finishBeforeDestroy() {
			beforeDestroy.run();
		}
	}
}
