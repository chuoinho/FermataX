package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.R;

@RunWith(RobolectricTestRunner.class)
public class GlassLimitsSignalsViewTest {

    private Context context;
    private GlassLimitsSignalsView view;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        view = new GlassLimitsSignalsView(context);
    }

    @Test
    public void normalState() {
        view.show(SpeedLimit.kmh(50), 47f, "Leopoldstraße", 180f, 0.55f);

        assertEquals("47", view.getSpeedText());
        assertEquals("KM/H", view.getSpeedUnitText());
        assertEquals(context.getString(R.string.glass_speed_limit_desc, 50), view.getSignContentDescription());
        assertEquals(context.getString(R.string.glass_signal_desc, 180), view.getChipContentDescription());
        assertEquals("Leopoldstraße", view.getRoadNameText());
    }

    @Test
    public void mphConvertsSpeed() {
        // 80.5 km/h is ~50.02 mph -> rounds to 50
        view.show(SpeedLimit.mph(30), 80.5f, "Highway 1", -1f, 0f);

        assertEquals("50", view.getSpeedText());
        assertEquals("MPH", view.getSpeedUnitText());
        assertEquals(context.getString(R.string.glass_speed_limit_desc, 30), view.getSignContentDescription());
    }

    @Test
    public void noDataKeepsLayoutHeight() {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        view.show(SpeedLimit.kmh(50), 47f, "Leopoldstraße", 180f, 0.55f);
        view.measure(widthSpec, heightSpec);
        int normalHeight = view.getMeasuredHeight();

        view.show(null, 47f, null, -1f, 0f);
        view.measure(widthSpec, heightSpec);
        int noDataHeight = view.getMeasuredHeight();

        view.showWaiting();
        view.measure(widthSpec, heightSpec);
        int waitingHeight = view.getMeasuredHeight();

        assertTrue(normalHeight > 0);
        assertEquals(normalHeight, noDataHeight);
        assertEquals(normalHeight, waitingHeight);
    }

    @Test
    public void noSignalShowsMutedText() {
        view.show(SpeedLimit.kmh(50), 47f, "Main St", -1f, 0f);

        assertEquals(context.getString(R.string.glass_signal_none), view.getChipText());
    }

    @Test
    public void swapButtonHasDescriptionAndCallsListener() {
        AtomicBoolean clicked = new AtomicBoolean(false);
        view.setOnSwapListener(() -> clicked.set(true));

        View swapBtn = view.getSwapButton();
        assertNotNull(swapBtn);
        assertEquals(context.getString(R.string.glass_show_speed), swapBtn.getContentDescription());

        swapBtn.performClick();
        assertTrue(clicked.get());
    }
}
