package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class BackgroundRunStateTest {
	private Context context;

	@Before
	public void resetPreferences() {
		context = RuntimeEnvironment.getApplication();
		context.getSharedPreferences("background_run", Context.MODE_PRIVATE).edit().clear().commit();
	}

	@After
	public void clearPreferences() {
		context.getSharedPreferences("background_run", Context.MODE_PRIVATE).edit().clear().commit();
	}

	@Test
	public void firstRunWarningAndVendorGuideCompletionArePersistedIndependently() {
		assertFalse(BackgroundRunState.wasInitialWarningAsked(context));
		assertFalse(BackgroundRunState.isVendorGuideDone(context));

		BackgroundRunState.markInitialWarningAsked(context);
		BackgroundRunState.setVendorGuideDone(context, true);

		assertTrue(BackgroundRunState.wasInitialWarningAsked(context));
		assertTrue(BackgroundRunState.isVendorGuideDone(context));
		BackgroundRunState.setVendorGuideDone(context, false);
		assertFalse(BackgroundRunState.isVendorGuideDone(context));
		assertTrue(BackgroundRunState.wasInitialWarningAsked(context));
	}
}
