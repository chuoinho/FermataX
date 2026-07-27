package me.aap.fermata.addon.stremio.ui.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class StremioSourceKeyboardTest {
	@Test
	public void nextFocusesTokenAndDoneSubmitsExactlyOnce() {
		Context context = RuntimeEnvironment.getApplication();
		EditText url = new EditText(context);
		EditText token = new EditText(context);
		AtomicInteger submissions = new AtomicInteger();
		StremioSourceEditorDialog.configureKeyboardFlow(url, token,
				submissions::incrementAndGet);

		assertEquals(EditorInfo.IME_ACTION_NEXT, url.getImeOptions());
		assertEquals(EditorInfo.IME_ACTION_DONE, token.getImeOptions());
		url.onEditorAction(EditorInfo.IME_ACTION_NEXT);
		assertTrue(token.isFocused());
		token.onEditorAction(EditorInfo.IME_ACTION_DONE);
		assertEquals(1, submissions.get());
	}

	@Test
	public void projectedBindingDoesNotReopenFromFocusRestoration() {
		Context context = RuntimeEnvironment.getApplication();
		EditText field = new EditText(context);
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.bind(field, 1, () -> {
		});

		assertNull(field.getOnFocusChangeListener());
		field.performClick();
		assertEquals(1, harness.requests.size());
		harness.requests.get(0).complete("https://example.test/manifest.json");
		field.requestFocus();

		assertEquals("https://example.test/manifest.json", field.getText().toString());
		assertEquals(1, harness.requests.size());
	}

	@Test
	public void projectedInitialOpenWaitsForVisibleDialog() {
		Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		EditText field = new EditText(activity);
		activity.setContentView(field);
		AtomicBoolean ready = new AtomicBoolean();
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.openWhenReady(field, 7, () -> {
		}, ready::get);

		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150));
		assertEquals(0, harness.requests.size());
		ready.set(true);
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));

		assertEquals(List.of(7), harness.titles);
		assertEquals(1, harness.requests.size());
	}

	@Test
	public void projectedCloseCancelsPendingInitialOpen() {
		Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		EditText field = new EditText(activity);
		activity.setContentView(field);
		AtomicBoolean ready = new AtomicBoolean();
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.openWhenReady(field, 7, () -> {
		}, ready::get);
		editor.close();
		ready.set(true);

		Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
		assertEquals(0, harness.requests.size());
		assertEquals(1, harness.stops.get());
	}

	@Test
	public void projectedUrlAcceptsThenTokenSubmitsExactlyOnce() {
		Context context = RuntimeEnvironment.getApplication();
		EditText url = new EditText(context);
		EditText token = new EditText(context);
		AtomicInteger submissions = new AtomicInteger();
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.bind(url, 11, token::performClick);
		editor.bind(token, 22, submissions::incrementAndGet);

		url.performClick();
		assertEquals(List.of(11), harness.titles);
		harness.requests.get(0).complete("https://example.test/manifest.json");

		assertEquals("https://example.test/manifest.json", url.getText().toString());
		assertEquals(List.of(11, 22), harness.titles);
		assertEquals(2, harness.requests.size());

		harness.requests.get(1).complete("secret");
		assertEquals("secret", token.getText().toString());
		assertEquals(1, submissions.get());
		assertEquals(2, harness.requests.size());
	}

	@Test
	public void projectedCancelDoesNotAcceptAndExplicitRetryStillWorks() {
		Context context = RuntimeEnvironment.getApplication();
		EditText field = new EditText(context);
		AtomicInteger accepted = new AtomicInteger();
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.bind(field, 1, accepted::incrementAndGet);

		field.performClick();
		harness.requests.get(0).cancel();
		assertEquals(0, accepted.get());
		assertEquals(1, harness.requests.size());

		field.performClick();
		assertEquals(2, harness.requests.size());
		harness.requests.get(1).complete("retry");
		assertEquals("retry", field.getText().toString());
		assertEquals(1, accepted.get());
	}

	@Test
	public void projectedCloseCancelsAndRejectsLateClicks() {
		Context context = RuntimeEnvironment.getApplication();
		EditText field = new EditText(context);
		AtomicInteger accepted = new AtomicInteger();
		ProjectedHarness harness = new ProjectedHarness();
		StremioSourceEditorDialog.ProjectedFieldEditor editor = harness.create();
		editor.bind(field, 1, accepted::incrementAndGet);

		field.performClick();
		Promise<String> request = harness.requests.get(0);
		editor.close();
		editor.close();
		field.performClick();

		assertTrue(request.isCancelled());
		assertEquals(1, harness.stops.get());
		assertEquals(1, harness.requests.size());
		assertEquals(0, accepted.get());
	}

	@Test
	public void projectedFallbackLeavesFieldOnPlatformImePath() {
		Context context = RuntimeEnvironment.getApplication();
		EditText field = new EditText(context);
		AtomicInteger fallback = new AtomicInteger();
		StremioSourceEditorDialog.ProjectedFieldEditor editor =
				new StremioSourceEditorDialog.ProjectedFieldEditor(
						(editText, title) -> null, editText -> fallback.incrementAndGet(), () -> {
						});
		editor.bind(field, 1, () -> {
		});

		field.performClick();
		field.performClick();

		assertEquals(2, fallback.get());
	}

	private static final class ProjectedHarness {
		private final List<Integer> titles = new ArrayList<>();
		private final List<Promise<String>> requests = new ArrayList<>();
		private final AtomicInteger stops = new AtomicInteger();

		private StremioSourceEditorDialog.ProjectedFieldEditor create() {
			return new StremioSourceEditorDialog.ProjectedFieldEditor(
					this::request, field -> {
					}, stops::incrementAndGet);
		}

		private FutureSupplier<String> request(EditText field, Integer title) {
			titles.add(title);
			Promise<String> request = new Promise<>();
			requests.add(request);
			return request;
		}
	}
}
