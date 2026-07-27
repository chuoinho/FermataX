package me.aap.fermata.addon.stremio.ui.config;

import static org.junit.Assert.assertEquals;

import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StremioConfigWebViewDeviceTest {
	@Test
	public void projectedInputUpdatesFieldWithoutSubmittingForm() throws Exception {
		var instrumentation = InstrumentationRegistry.getInstrumentation();
		AtomicReference<StremioConfigWebView> viewRef = new AtomicReference<>();
		CountDownLatch loaded = new CountDownLatch(1);
		instrumentation.runOnMainSync(() -> {
			StremioConfigWebView view = new StremioConfigWebView(
					instrumentation.getTargetContext());
			view.getSettings().setJavaScriptEnabled(true);
			view.setWebViewClient(new android.webkit.WebViewClient() {
				@Override
				public void onPageFinished(WebView ignored, String url) {
					loaded.countDown();
				}
			});
			view.loadDataWithBaseURL("https://provider.example.invalid/config",
					"<form onsubmit='window.submits=(window.submits||0)+1;return false'>" +
							"<input id='user' data-fermatax-input-token='fx-device'></form>",
					"text/html", "UTF-8", null);
			viewRef.set(view);
		});
		if (!loaded.await(10, TimeUnit.SECONDS)) throw new AssertionError("WebView did not load");

		AtomicReference<String> result = new AtomicReference<>();
		CountDownLatch evaluated = new CountDownLatch(1);
		instrumentation.runOnMainSync(() -> {
			StremioConfigWebView view = viewRef.get();
			view.evaluateJavascript("document.getElementById('user').focus()", ignored ->
					view.evaluateJavascript(StremioConfigWebView.applyInputScript("fx-device",
							"https://provider.example.invalid/config", "driver"), applied ->
							view.evaluateJavascript("JSON.stringify([document.getElementById('user').value," +
									"window.submits||0])", value -> {
								result.set(value);
								evaluated.countDown();
							})));
		});
		if (!evaluated.await(10, TimeUnit.SECONDS)) throw new AssertionError("JS did not complete");
		assertEquals("\"[\\\"driver\\\",0]\"", result.get());
		instrumentation.runOnMainSync(() -> viewRef.get().destroy());
	}
}
