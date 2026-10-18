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

    @Test
    public void testSectionHeaderDecoration() {
        Context ctx = RuntimeEnvironment.getApplication();
        View headerRow = new View(ctx);
        headerRow.setId(me.aap.fermata.R.id.settings_section_header);
        headerRow.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setBackground(new android.graphics.drawable.ColorDrawable(0xFF000000));
        headerRow.setMinimumHeight(100);

        GlassSettingsDecorator.decorateRow(headerRow, true);
        assertNull(headerRow.getBackground());
        assertEquals(0, headerRow.getMinimumHeight());
        assertEquals(0, headerRow.getPaddingLeft());
        assertEquals(0, headerRow.getPaddingTop());
        assertFalse(headerRow.isFocusable());
        assertFalse(headerRow.isClickable());
    }

    @Test
    public void testSectionHeaderDetectionWithViewOpts() {
        Context ctx = RuntimeEnvironment.getApplication();
        me.aap.utils.pref.PreferenceView pv = new me.aap.utils.pref.PreferenceView(ctx);
        me.aap.utils.pref.PreferenceView.ViewOpts opts = new me.aap.utils.pref.PreferenceView.ViewOpts();
        opts.view = () -> new android.widget.TextView(ctx);
        pv.setPreference(null, () -> opts);

        assertTrue(GlassSettingsDecorator.isSectionHeader(pv));
        GlassSettingsDecorator.decorateRow(pv, true);
        assertNull(pv.getBackground());
        assertEquals(0, pv.getMinimumHeight());
        assertEquals(0, pv.getPaddingLeft());
    }

    @Test
    public void testRecycledPreferenceViewRecoversCardDecoration() {
        Context ctx = RuntimeEnvironment.getApplication();
        me.aap.utils.pref.PreferenceView pv = new me.aap.utils.pref.PreferenceView(ctx);

        // 1. Initially bound as a section header
        me.aap.utils.pref.PreferenceView.ViewOpts headerOpts = new me.aap.utils.pref.PreferenceView.ViewOpts();
        headerOpts.view = () -> new android.widget.TextView(ctx);
        pv.setPreference(null, () -> headerOpts);
        pv.setTag(me.aap.fermata.R.id.settings_section_header, Boolean.TRUE);
        GlassSettingsDecorator.decorateRow(pv, true);
        assertTrue(GlassSettingsDecorator.isSectionHeader(pv));
        assertNull(pv.getBackground());

        // 2. Recycled / rebound with non-header preference
        pv.setPreference(null, null);

        // Verify tag is cleared and isSectionHeader returns false
        assertFalse(GlassSettingsDecorator.isSectionHeader(pv));
        assertNull(pv.getTag(me.aap.fermata.R.id.settings_section_header));

        // Decorate row and verify card background and dimensions are restored
        pv.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        GlassSettingsDecorator.decorateRow(pv, true);
        assertNotNull(pv.getBackground());
        assertTrue(pv.getMinimumHeight() > 0);
        assertTrue(pv.isFocusable());
        assertTrue(pv.isClickable());
    }
}
