package me.app.fermatax.auto.carapps;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
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

import java.lang.reflect.Field;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootProbe;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.CarService;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class RootGrantTest {

	private Context context;

	@Before
	public void setUp() throws Exception {
		context = RuntimeEnvironment.getApplication();
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		CarAppsFeature.setState(RootState.AVAILABLE);
		setCarServiceActive(false);
	}

	@After
	public void tearDown() throws Exception {
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		CarAppsFeature.setState(RootState.ABSENT);
		setCarServiceActive(false);
	}

	private void setCarServiceActive(boolean active) throws Exception {
		Field f = CarService.class.getDeclaredField("active");
		f.setAccessible(true);
		f.setBoolean(null, active);
	}

	@Test
	public void denyLeavesStateAvailableAndRecordsNothing() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		boolean granted = RootGrant.request(context, () -> failed(new SecurityException("root denied"))).get(() -> false);
		assertFalse(granted);
		assertEquals(RootState.AVAILABLE, CarAppsFeature.state());
		assertFalse(context.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean(RootProbe.PREF_ROOT_GRANTED, false));
	}

	@Test
	public void grantIsAllowedWhileCarServiceActive() throws Exception {
		setCarServiceActive(true);
		boolean granted = RootGrant.request(context, () -> completed((me.app.fermatax.auto.Su) null)).get(() -> false);
		assertTrue(granted);
	}
}
