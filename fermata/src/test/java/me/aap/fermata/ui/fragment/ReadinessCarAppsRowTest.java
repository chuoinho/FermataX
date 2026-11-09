package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.ui.carapps.CarAppsReadinessModel;
import me.aap.fermata.ui.carapps.RootState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class ReadinessCarAppsRowTest {
	@Test
	public void phoneAppsRowIsOnlyShownInAutoBuilds() {
		CarAppsReadinessModel visible = CarAppsReadinessModel.from(RootState.ABSENT, 0, 30);
		assertTrue(ReadinessPrefsBuilder.shouldAddCarAppsRow(true, visible));
		assertFalse(ReadinessPrefsBuilder.shouldAddCarAppsRow(false, visible));
		assertFalse(ReadinessPrefsBuilder.shouldAddCarAppsRow(true,
				CarAppsReadinessModel.from(RootState.GRANTED, 0, 32)));
	}

	@Test
	public void unrootedAndroid11And12RowExplainsRestrictionWithoutGrantAction() {
		Context context = RuntimeEnvironment.getApplication();
		for (int sdk : new int[]{30, 31}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.ABSENT, 0, sdk);
			assertEquals("Root access required · Android 11–12 blocks the car keyboard on untrusted displays",
					ReadinessPrefsBuilder.carAppsSubtitle(context, model));
			assertEquals(0, model.actionStringRes);
		}
	}

	@Test
	public void existingGrantRootActionStaysVisibleAlongsideKeyboardReason() {
		Context context = RuntimeEnvironment.getApplication();
		for (int sdk : new int[]{30, 31}) {
			CarAppsReadinessModel model = CarAppsReadinessModel.from(RootState.AVAILABLE, 0, sdk);
			assertEquals("Root access required · Grant root · Android 11–12 blocks the car keyboard on untrusted displays",
					ReadinessPrefsBuilder.carAppsSubtitle(context, model));
		}
		assertEquals("Ready", ReadinessPrefsBuilder.carAppsSubtitle(context,
				CarAppsReadinessModel.from(RootState.GRANTED, CarAppsReadinessModel.CAP_INJECT, 30)));
		assertEquals("Ready · Requires USB debugging (Security settings)",
				ReadinessPrefsBuilder.carAppsSubtitle(context,
						CarAppsReadinessModel.from(RootState.GRANTED, 0, 31)));
	}
}
