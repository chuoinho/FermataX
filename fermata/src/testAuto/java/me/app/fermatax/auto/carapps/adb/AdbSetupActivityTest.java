package me.app.fermatax.auto.carapps.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityPrefs;

/** Edge-to-edge (enforced from Android 15 for targetSdk 35): the setup screens must stay clear of the system bars. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class AdbSetupActivityTest {

	@Test
	public void systemBarInsetsBecomePaddingOfTheRoot() {
		AdbSetupActivity activity = Robolectric.buildActivity(AdbSetupActivity.class).setup().get();
		View root = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);

		root.dispatchApplyWindowInsets(new WindowInsets.Builder()
				.setInsets(WindowInsets.Type.systemBars(), Insets.of(10, 96, 20, 48)).build());

		assertEquals("the title is not under the status bar", 96, root.getPaddingTop());
		assertEquals(48, root.getPaddingBottom());
		assertEquals(10, root.getPaddingLeft());
		assertEquals(20, root.getPaddingRight());
	}

	@Test
	public void buttonTextHasReadableContrastAndTheTargetIsBigEnough() {
		AdbSetupActivity activity = Robolectric.buildActivity(AdbSetupActivity.class).setup().get();
		Button button = findButton(activity.findViewById(android.R.id.content));
		assertNotNull(button);
		assertNotNull("the button gets a themed fill, not the grey default", button.getBackgroundTintList());
		double contrast = ColorUtils.calculateContrast(button.getCurrentTextColor(),
				button.getBackgroundTintList().getDefaultColor());
		assertTrue("contrast " + contrast, contrast >= 4.5);
		float dp = activity.getResources().getDisplayMetrics().density;
		assertTrue(button.getMinHeight() >= Math.round(48 * dp));
		TextView title = (TextView) findText(activity.findViewById(android.R.id.content),
				activity.getString(R.string.car_apps_setup_title));
		assertNotNull(title);
		assertEquals(button.getText().toString(), button.getContentDescription().toString());
	}

	@Test
	public void theGuideFollowsTheAppLanguageNotThePhoneLanguage() {
		// The phone is English here; the app language is set to Russian.
		java.util.Locale defaultLocale = java.util.Locale.getDefault(); // the app's locale context also sets the default
		MainActivityPrefs prefs = MainActivityPrefs.get();
		prefs.applyIntPref(MainActivityPrefs.LOCALE, MainActivityPrefs.Lang.RU.ordinal());
		try {
			AdbSetupActivity activity = Robolectric.buildActivity(AdbSetupActivity.class).setup().get();
			assertEquals("ru", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
			assertNotNull(findText(activity.findViewById(android.R.id.content), "Настройка приложений телефона"));
		} finally {
			prefs.removePref(MainActivityPrefs.LOCALE);
			java.util.Locale.setDefault(defaultLocale);
		}
	}

	private static Button findButton(View v) {
		if (v instanceof Button) return (Button) v;
		if (v instanceof android.view.ViewGroup) {
			android.view.ViewGroup g = (android.view.ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				Button b = findButton(g.getChildAt(i));
				if (b != null) return b;
			}
		}
		return null;
	}

	private static View findText(View v, String text) {
		if ((v instanceof TextView) && text.contentEquals(((TextView) v).getText())) return v;
		if (v instanceof android.view.ViewGroup) {
			android.view.ViewGroup g = (android.view.ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = findText(g.getChildAt(i), text);
				if (f != null) return f;
			}
		}
		return null;
	}
}
