package me.aap.fermata.ui.glass.settings;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import me.aap.fermata.ui.control.PhoneReadiness;

@RunWith(RobolectricTestRunner.class)
public class GlassSettingsDecoratorTest {

    @Test
    public void testNotificationListenerReadinessRegistered() {
        Context ctx = RuntimeEnvironment.getApplication();
        boolean hasCheck = PhoneReadiness.hasNotificationListenerCheck(ctx);
        assertFalse(hasCheck);
        assertNotNull(PhoneReadiness.createNotificationListenerSettingsIntent());
    }

    @Test
    public void testRowDecoration() {
        Context ctx = RuntimeEnvironment.getApplication();
        View row = new View(ctx);
        row.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GlassSettingsDecorator.decorateRow(row, true);
        assertTrue(row.getMinimumHeight() > 0);
        assertNotNull(row.getBackground());
    }
}
