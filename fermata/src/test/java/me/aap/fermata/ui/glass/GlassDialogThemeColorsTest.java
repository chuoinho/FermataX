package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.ui.glass.dialog.GlassDialogBuilder;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class GlassDialogThemeColorsTest {

	@Test public void titleAndMessageUseGlassPrimaryAndSecondaryColorsInDayAndNight() {
		Application context = RuntimeEnvironment.getApplication();
		LinearLayout dialog = new LinearLayout(context);
		TextView title = new TextView(context);
		title.setId(androidx.appcompat.R.id.alertTitle);
		TextView message = new TextView(context);
		message.setId(android.R.id.message);
		dialog.addView(title);
		dialog.addView(message);

		for (boolean night : new boolean[]{false, true}) {
			GlassThemeColors colors = GlassThemeColors.get(context, night);
			GlassDialogBuilder.styleViewHierarchy(dialog, colors, night);
			assertEquals(colors.getTextPrimary(), title.getCurrentTextColor());
			assertEquals(colors.getTextSecondary(), message.getCurrentTextColor());
		}
	}
}
