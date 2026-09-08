package me.aap.fermata.ui.view;

import static android.view.View.MeasureSpec.EXACTLY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.media.audio.AudioEffectsController;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.R;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceView;

@RunWith(RobolectricTestRunner.class)
public class AudioEffectsScreenTouchTest {
	@Test
	public void emptyBandStripSwipeScrollsTheNestedPageAndKeepsActionsInBounds() throws Exception {
		Fixture fixture = fixture(800, 400);
		HorizontalScrollView bandScroll = find(fixture.screen, HorizontalScrollView.class);
		AudioEffectsBandView band = find(fixture.screen, AudioEffectsBandView.class);
		int before = band.getValueDb();
		int y = top(bandScroll, fixture.recycler) + bandScroll.getHeight() - 30;

		swipe(fixture.recycler, 20, y, 20, y - 350, 8);

		assertTrue("scale area should scroll the page: scrollY=" + fixture.vertical.getScrollY() +
				", bandScroll=" + bounds(bandScroll, fixture.recycler),
				fixture.vertical.getScrollY() > 0);
		assertEquals(before, band.getValueDb());
		ViewGroup actions = (ViewGroup) fixture.screen.getChildAt(1);
		assertTrue(actions.getTop() >= 0);
		assertTrue(actions.getBottom() <= fixture.screen.getHeight());
		assertEquals(fixture.screen.getHeight(), actions.getBottom());
		for (int i = 0; i < actions.getChildCount(); i++) {
			View child = actions.getChildAt(i);
			assertTrue(child.getTop() >= 0);
			assertTrue(child.getBottom() <= actions.getHeight());
		}
	}

	@Test
	public void verticalBandDragChangesOnlyTheBandValue() throws Exception {
		Fixture fixture = fixture(2340, 1080);
		AudioEffectsBandView band = find(fixture.screen, AudioEffectsBandView.class);
		int before = band.getValueDb();
		int x = left(band, fixture.recycler) + band.getWidth() / 2;
		int y = top(band, fixture.recycler) + band.getHeight() / 2;

		swipe(fixture.recycler, x, y, x, y + 80, 6);

		assertTrue("vertical drag should update the band: " + before + " -> " +
				band.getValueDb() + ", band=" + bounds(band, fixture.recycler),
				band.getValueDb() < before);
		assertEquals(0, fixture.vertical.getScrollY());
	}

	@Test
	public void horizontalBandGestureDoesNotChangeBandValueWhenBandsUseBankPaging() throws Exception {
		Fixture fixture = fixture(480, 1080);
		HorizontalScrollView bandScroll = find(fixture.screen, HorizontalScrollView.class);
		AudioEffectsBandView band = find(fixture.screen, AudioEffectsBandView.class);
		int before = band.getValueDb();
		int x = left(band, fixture.recycler) + band.getWidth() / 2;
		int y = top(band, fixture.recycler) + band.getHeight() / 2;

		swipe(fixture.recycler, x, y, x - 220, y, 8);

		assertEquals(before, band.getValueDb());
		assertEquals(0, bandScroll.getScrollX());
	}

	@Test
	public void compactViewportSizesKeepFixedActionsInsideTheScreen() throws Exception {
		for (int[] size : new int[][]{{360, 640}, {640, 320}, {800, 400}, {1024, 600}}) {
			Fixture fixture = fixture(size[0], size[1]);
			ViewGroup actions = (ViewGroup) fixture.screen.getChildAt(1);
			assertEquals("actions should stay fixed to the bottom at " + size[0] + "x" + size[1],
					size[1], actions.getBottom());
			assertTrue(actions.getTop() >= 0);
			for (int i = 0; i < actions.getChildCount(); i++) {
				View child = actions.getChildAt(i);
				assertTrue(child.getTop() >= 0);
				assertTrue(child.getBottom() <= actions.getHeight());
			}
		}
	}

	@Test
	public void preampSeekBarDragSurvivesTheNestedPreferenceParents() throws Exception {
		Fixture fixture = fixture(2340, 1080);
		SeekBar seek = find(fixture.screen, SeekBar.class);
		fixture.vertical.scrollTo(0, offsetTop(seek, fixture.content));
		int x = left(seek, fixture.recycler) + seek.getWidth() / 2;
		int y = top(seek, fixture.recycler) + seek.getHeight() / 2;

		swipe(fixture.recycler, x, y, left(seek, fixture.recycler) + 10, y, 8);

		assertTrue("preamp SeekBar should receive a horizontal drag: value=" +
				fixture.store.getIntPref(AudioEffectsProfileRepository.PREAMP_DB) + ", seek=" +
				bounds(seek, fixture.recycler),
				fixture.store.getIntPref(
					AudioEffectsProfileRepository.PREAMP_DB) < 0);
	}

	@Test
	public void editorUsesOneColumnAndShowsAllBandsWhenTheMeasuredContentFits() throws Exception {
		Fixture fixture = fixture(800, 400);
		LinearLayout body = (LinearLayout) fixture.content.getChildAt(2);

		assertEquals(LinearLayout.VERTICAL, body.getOrientation());
		assertEquals(2, body.getChildCount());
		assertEquals(10, visibleBands(fixture.screen));
	}

	@Test
	public void narrowEditorUsesTwoReadableBandGroupsAndScrollsToTheLastEffect() throws Exception {
		Fixture fixture = fixture(320, 240);

		assertEquals(5, visibleBands(fixture.screen));
		fixture.vertical.fullScroll(View.FOCUS_DOWN);
		SeekBar lastSeek = findAll(fixture.screen, SeekBar.class).get(
				findAll(fixture.screen, SeekBar.class).size() - 1);
		assertTrue(lastSeek.getBottom() - fixture.vertical.getScrollY() <= fixture.vertical.getHeight());
	}

	@Test
	public void narrowDraftActionsPlaceStatusAboveButtons() throws Exception {
		Fixture fixture = fixture(320, 240);
		LinearLayout actions = (LinearLayout) fixture.screen.getChildAt(1);

		assertEquals(LinearLayout.VERTICAL, actions.getOrientation());
		assertTrue(actions.getChildAt(0).getBottom() <= actions.getChildAt(1).getTop());
	}

	private static Fixture fixture(int width, int height) throws Exception {
		Context context = new ResourceContext(RuntimeEnvironment.getApplication());
		BasicPreferenceStore store = new BasicPreferenceStore();
		AudioEffectsDraft draft = new AudioEffectsDraft(new AudioEffectsProfileRepository(store));
		draft.getStore().applyBooleanPref(AudioEffectsProfileRepository.ENABLED, true);
		MediaSessionCallback callback = callback(store);
		RecyclerView recycler = new RecyclerView(context);
		recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
			context));
		recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
				PreferenceView view = new PreferenceView(parent.getContext());
				view.setLayoutParams(new RecyclerView.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				return new RecyclerView.ViewHolder(view) {};
			}

			@Override
			public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
				PreferenceView.ViewOpts options = new PreferenceView.ViewOpts();
				options.view = () -> screenForTest(context, draft, callback);
				((PreferenceView) holder.itemView).setPreference(null, () -> options);
			}

			@Override
			public int getItemCount() {
				return 1;
			}
		});
		Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		activity.setContentView(recycler);
		int widthSpec = View.MeasureSpec.makeMeasureSpec(width, EXACTLY);
		int heightSpec = View.MeasureSpec.makeMeasureSpec(height, EXACTLY);
		recycler.measure(widthSpec, heightSpec);
		recycler.layout(0, 0, width, height);
		PreferenceView preference = (PreferenceView) recycler.getChildAt(0);
		AudioEffectsScreenView screen = (AudioEffectsScreenView) preference.getChildAt(0);
		ScrollView vertical = find(screen, ScrollView.class);
		return new Fixture((BasicPreferenceStore) draft.getStore(), recycler, screen, vertical,
				(ViewGroup) vertical.getChildAt(0));
	}

	private static AudioEffectsScreenView screenForTest(Context context,
			AudioEffectsDraft draft, MediaSessionCallback callback) {
		AudioEffectsScreenView screen = new AudioEffectsScreenView(context, draft, callback);
		clearSwitchText(screen);
		return screen;
	}

	private static void clearSwitchText(View root) {
		if (root instanceof SwitchCompat toggle) {
			toggle.setText("");
			toggle.setTextOn("");
			toggle.setTextOff("");
		}
		if (root instanceof ViewGroup group) {
			for (int i = 0; i < group.getChildCount(); i++) clearSwitchText(group.getChildAt(i));
		}
	}

	private static final class ResourceContext extends ContextThemeWrapper {
		private final Resources resources;

		ResourceContext(Context base) {
			super(base, base.getTheme());
			Resources source = base.getResources();
			resources = new Resources(source.getAssets(), source.getDisplayMetrics(),
					source.getConfiguration()) {
				@Override
				public CharSequence getText(int id) throws NotFoundException {
					if (isFermataString(id)) return "test";
					return source.getText(id);
				}

				@Override
				public String getString(int id) throws NotFoundException {
					if (isFermataString(id)) return "test";
					return source.getString(id);
				}

				@Override
				public String getString(int id, Object... formatArgs) throws NotFoundException {
					if (isFermataString(id)) return "test";
					return source.getString(id, formatArgs);
				}

				@Override
				public String[] getStringArray(int id) throws NotFoundException {
					if (id == R.array.audio_effects_presets) {
						return new String[]{"Flat", "Pop", "Rock", "Classical", "Dance", "Custom"};
					}
					if (id == R.array.audio_effects_band_banks) {
						return new String[]{"31-500 Hz", "1-16 kHz"};
					}
					return source.getStringArray(id);
				}
			};
		}

		@Override
		public Resources getResources() {
			return resources;
		}

		private static boolean isFermataString(int id) {
			return (id & 0xffff0000) == (R.string.cancel & 0xffff0000);
		}
	}

	private static MediaSessionCallback callback(BasicPreferenceStore store) throws Exception {
		MediaSessionCallback callback = allocate(MediaSessionCallback.class);
		AudioEffectsController controller = new AudioEffectsController(store, () -> {});
		Field backend = AudioEffectsController.class.getDeclaredField("backend");
		backend.setAccessible(true);
		Class<?> backendType = Class.forName("me.aap.fermata.media.audio.AudioEffectsBackend");
		backend.set(controller, Proxy.newProxyInstance(backendType.getClassLoader(),
				new Class<?>[]{backendType}, (ignored, method, args) ->
						method.getName().equals("getCapabilities") ?
								EnumSet.of(me.aap.fermata.media.audio.AudioEffectCapability.VIRTUALIZER) :
								null));
		set(callback, "audioEffectsController", controller);
		return callback;
	}

	private static void swipe(View root, int downX, int downY, int upX, int upY, int steps) {
		long downTime = 1_000L;
		dispatch(root, MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
				downX, downY, 0));
		for (int i = 1; i < steps; i++) {
			float fraction = i / (float) steps;
			dispatch(root, MotionEvent.obtain(downTime, downTime + i * 16L, MotionEvent.ACTION_MOVE,
				downX + ((upX - downX) * fraction), downY + ((upY - downY) * fraction), 0));
		}
		dispatch(root, MotionEvent.obtain(downTime, downTime + steps * 16L,
				MotionEvent.ACTION_UP, upX, upY, 0));
	}

	private static void dispatch(View root, MotionEvent event) {
		try {
			root.dispatchTouchEvent(event);
		} finally {
			event.recycle();
		}
	}

	private static int left(View view, View root) {
		int value = 0;
		for (View current = view; current != root; ) {
			value += current.getLeft();
			ViewParent parent = current.getParent();
			if (!(parent instanceof View)) throw new AssertionError("view is outside root");
			current = (View) parent;
			value -= current.getScrollX();
		}
		return value;
	}

	private static int top(View view, View root) {
		int value = 0;
		for (View current = view; current != root; ) {
			value += current.getTop();
			ViewParent parent = current.getParent();
			if (!(parent instanceof View)) throw new AssertionError("view is outside root");
			current = (View) parent;
			value -= current.getScrollY();
		}
		return value;
	}

	private static int offsetTop(View view, View ancestor) {
		int value = 0;
		for (View current = view; current != ancestor; ) {
			value += current.getTop();
			ViewParent parent = current.getParent();
			if (!(parent instanceof View)) throw new AssertionError("view is outside ancestor");
			current = (View) parent;
		}
		return value;
	}

	private static String bounds(View view, View root) {
		return "[" + left(view, root) + "," + top(view, root) + "," +
				(view.getWidth()) + "x" + view.getHeight() + "]";
	}

	private static <T extends View> T find(View root, Class<T> type) {
		T found = findOrNull(root, type);
		if (found == null) throw new AssertionError("missing " + type.getSimpleName());
		return found;
	}

	private static int visibleBands(View root) {
		int visible = 0;
		for (AudioEffectsBandView band : findAll(root, AudioEffectsBandView.class)) {
			if (band.getVisibility() == View.VISIBLE) visible++;
		}
		return visible;
	}

	private static <T extends View> java.util.List<T> findAll(View root, Class<T> type) {
		java.util.List<T> found = new java.util.ArrayList<>();
		collect(root, type, found);
		return found;
	}

	private static <T extends View> void collect(View root, Class<T> type,
			java.util.List<T> found) {
		if (type.isInstance(root)) found.add(type.cast(root));
		if (root instanceof ViewGroup group) {
			for (int i = 0; i < group.getChildCount(); i++) {
				collect(group.getChildAt(i), type, found);
			}
		}
	}

	private static <T extends View> T findOrNull(View root, Class<T> type) {
		if (type.isInstance(root)) return type.cast(root);
		if (root instanceof ViewGroup group) {
			for (int i = 0; i < group.getChildCount(); i++) {
				T found = findOrNull(group.getChildAt(i), type);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static <T> T allocate(Class<T> type) throws Exception {
		Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
		Field field = unsafeType.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Object unsafe = field.get(null);
		return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}

	private static void set(Object target, String name, Object value) throws Exception {
		Field field = MediaSessionCallback.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private record Fixture(BasicPreferenceStore store, RecyclerView recycler,
			AudioEffectsScreenView screen, ScrollView vertical, ViewGroup content) {}
}
