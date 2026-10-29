package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDisplayManager;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore.Pref;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = FermataApplication.class)
public class LauncherActivityDisplayTest {

	private static final Pref<Supplier<String[]>> AA_LAUNCHER_APPS = Pref.sa("AA_LAUNCHER_APPS",
			() -> new String[]{FermataApplication.get().getPackageName(), "com.android.chrome",
					"com.google.android.gm", "com.google.android.apps.maps", "com.google.android.youtube"});

	private static final String TEST_PKG = "com.example.testapp";
	private static final String TEST_CLS = "com.example.testapp.MainActivity";

	public static class TestVirtualLauncherActivity extends LauncherActivity {
		private Display display;

		public void setDisplay(Display display) {
			this.display = display;
		}

		@Override
		public Display getDisplay() {
			return display != null ? display : super.getDisplay();
		}
	}

	@Before
	public void setUp() {
		FermataApplication app = (FermataApplication) RuntimeEnvironment.getApplication();

		Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
		launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

		ResolveInfo resolveInfo = new ResolveInfo();
		resolveInfo.activityInfo = new ActivityInfo();
		resolveInfo.activityInfo.packageName = TEST_PKG;
		resolveInfo.activityInfo.name = TEST_CLS;
		resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
		resolveInfo.activityInfo.applicationInfo.packageName = TEST_PKG;

		ShadowPackageManager spm = Shadows.shadowOf(app.getPackageManager());
		spm.addResolveInfoForIntent(launcherIntent, resolveInfo);

		String sampleApp = TEST_PKG + "#" + TEST_CLS;
		String exitApp = app.getPackageName() + "#exit";
		MainActivityPrefs.get().applyStringArrayPref(AA_LAUNCHER_APPS, new String[]{sampleApp, exitApp});
	}

	@After
	public void tearDown() {
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
	}

	private Display createSecondaryDisplay() {
		Context context = RuntimeEnvironment.getApplication();
		int displayId = ShadowDisplayManager.addDisplay("w1024dp-h600dp");
		DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
		return dm.getDisplay(displayId);
	}

	private LauncherActivity launchOnDisplay(Display display) {
		if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
			ActivityController<TestVirtualLauncherActivity> controller =
					Robolectric.buildActivity(TestVirtualLauncherActivity.class);
			TestVirtualLauncherActivity activity = controller.get();
			activity.setDisplay(display);
			controller.create().start().resume().visible();
			return activity;
		} else {
			ActivityController<LauncherActivity> controller =
					Robolectric.buildActivity(LauncherActivity.class);
			controller.create().start().resume().visible();
			return controller.get();
		}
	}

	private RecyclerView getRecyclerView(LauncherActivity activity) {
		ViewGroup content = activity.findViewById(android.R.id.content);
		return (RecyclerView) content.getChildAt(0);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private boolean containsExitTile(LauncherActivity activity) {
		RecyclerView rv = getRecyclerView(activity);
		RecyclerView.Adapter adapter = rv.getAdapter();
		assertNotNull(adapter);
		String exitText = activity.getString(R.string.exit);

		for (int i = 0; i < adapter.getItemCount(); i++) {
			RecyclerView.ViewHolder holder = adapter.createViewHolder(rv, adapter.getItemViewType(i));
			adapter.bindViewHolder(holder, i);
			ViewGroup itemView = (ViewGroup) holder.itemView;
			for (int j = 0; j < itemView.getChildCount(); j++) {
				View child = itemView.getChildAt(j);
				if (child instanceof TextView tv && exitText.contentEquals(tv.getText())) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void clickAppTile(LauncherActivity activity, int position) {
		RecyclerView rv = getRecyclerView(activity);
		RecyclerView.Adapter adapter = rv.getAdapter();
		assertNotNull(adapter);
		RecyclerView.ViewHolder holder = adapter.createViewHolder(rv, adapter.getItemViewType(position));
		adapter.bindViewHolder(holder, position);
		holder.itemView.performClick();
	}

	@Test
	public void defaultDisplayKeepsMirrorPath() {
		LauncherActivity activity = launchOnDisplay(null);
		clickAppTile(activity, 0);

		Intent startedIntent = Shadows.shadowOf(activity).getNextStartedActivity();
		assertNotNull(startedIntent);
		assertEquals(TEST_PKG, startedIntent.getComponent().getPackageName());
		assertEquals(TEST_CLS, startedIntent.getComponent().getClassName());
		assertTrue("Default display must inject mirroring mode extra",
				startedIntent.hasExtra(LauncherActivity.INTENT_EXTRA_MODE));
		assertEquals(FermataApplication.get().getMirroringMode(),
				startedIntent.getIntExtra(LauncherActivity.INTENT_EXTRA_MODE, -1));
	}

	@Test
	public void virtualDisplaySkipsMirrorModeExtra() {
		Display virtualDisplay = createSecondaryDisplay();
		LauncherActivity activity = launchOnDisplay(virtualDisplay);
		clickAppTile(activity, 0);

		Intent startedIntent = Shadows.shadowOf(activity).getNextStartedActivity();
		assertNotNull(startedIntent);
		assertEquals(TEST_PKG, startedIntent.getComponent().getPackageName());
		assertEquals(TEST_CLS, startedIntent.getComponent().getClassName());
		assertFalse("Virtual display must not inject mirroring mode extra",
				startedIntent.hasExtra(LauncherActivity.INTENT_EXTRA_MODE));
	}

	@Test
	public void virtualDisplayHidesExitTile() {
		// Confirm on default display, exit tile is present
		LauncherActivity defaultActivity = launchOnDisplay(null);
		assertTrue("Default display must have exit tile", containsExitTile(defaultActivity));
		LauncherActivity.AppListView defaultListView = (LauncherActivity.AppListView) getRecyclerView(defaultActivity);
		assertTrue("Default display apps list must contain AppInfo.EXIT",
				defaultListView.apps.contains(LauncherActivity.AppListView.AppInfo.EXIT));

		// On virtual display, exit tile must be hidden/omitted
		Display virtualDisplay = createSecondaryDisplay();
		LauncherActivity virtualActivity = launchOnDisplay(virtualDisplay);
		assertFalse("Virtual display must hide/omit exit tile", containsExitTile(virtualActivity));
		LauncherActivity.AppListView virtualListView = (LauncherActivity.AppListView) getRecyclerView(virtualActivity);
		assertFalse("Virtual display apps list must not contain AppInfo.EXIT",
				virtualListView.apps.contains(LauncherActivity.AppListView.AppInfo.EXIT));
	}
}
