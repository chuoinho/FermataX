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
import me.aap.fermata.ui.glass.theme.GlassDimensions;

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
        view.show(SpeedLimit.kmh(50), "Leopoldstraße", 180f, 0.55f);

        assertEquals(context.getString(R.string.glass_speed_limit_desc, 50), view.getSignContentDescription());
        assertEquals(context.getString(R.string.glass_signal_desc, 180), view.getChipContentDescription());
        assertEquals("180 m", view.getChipText());
    }

    @Test
    public void speedIsTheOneTheSpeedCardShows() {
        view.show(SpeedLimit.kmh(30), "Highway 1", -1f, 0f);
        view.showSpeed("50", "MPH");

        assertEquals("50", view.getSpeedText());
        assertEquals("MPH", view.getSpeedUnitText());
        // A road update keeps the speed until the speed card sends the next one.
        view.show(SpeedLimit.kmh(30), "Highway 1", -1f, 0f);
        assertEquals("50", view.getSpeedText());
    }

    @Test
    public void noDataKeepsLayoutHeight() {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        view.show(SpeedLimit.kmh(50), "Leopoldstraße", 180f, 0.55f);
        view.measure(widthSpec, heightSpec);
        int normalHeight = view.getMeasuredHeight();

        view.show(null, null, -1f, 0f);
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
    public void noTrafficLightShowsRoadName() {
        view.show(SpeedLimit.kmh(50), "Main St", -1f, 0f);

        assertEquals("Main St", view.getChipText());
        assertEquals("Main St", view.getChipContentDescription());
    }

    @Test
    public void unnamedRoadSaysSo() {
        view.show(null, "", -1f, 0f);

        assertEquals(context.getString(R.string.glass_road_unnamed), view.getChipText());
    }

    @Test
    public void noMatchedRoadSaysNothingAboutTrafficLights() {
        view.show(null, null, -1f, 0f);

        assertEquals(context.getString(R.string.glass_road_searching), view.getChipText());
    }

    @Test
    public void missingLimitIsOneMessageNotTwo() {
        view.show(null, "Main St", -1f, 0f);

        // The grey sign says it; the text is left to screen readers so the card shows a single line.
        assertEquals(context.getString(R.string.glass_limits_no_data), view.getSignContentDescription());
        assertEquals(0, visibleTexts(context.getString(R.string.glass_limits_no_data)));
        assertEquals(1, visibleTexts("Main St"));
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

    @Test
    public void swapButtonMatchesCornerIconOfOtherCards() {
        // Same 28dp corner icon as the Weather / Navigation switch, so card headers line up.
        int size = Math.round(28 * context.getResources().getDisplayMetrics().density);
        View swapBtn = view.getSwapButton();
        assertEquals(size, swapBtn.getLayoutParams().width);
        assertEquals(size, swapBtn.getLayoutParams().height);
    }

    @Test
    public void swapButtonDoesNotPushTitleDown() {
        view.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 300, 400);
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        view.findViewsWithText(found, context.getString(R.string.glass_limits_title), View.FIND_VIEWS_WITH_TEXT);
        TextView title = (TextView) found.get(0);
        View btn = view.getSwapButton();
        int titleTop = topInCard(title);

        // Title starts right at the card's top padding, like titles on cards without a button.
        assertEquals(view.getPaddingTop(), titleTop);
        assertEquals(titleTop + title.getHeight() / 2f, topInCard(btn) + btn.getHeight() / 2f, 1f);
    }

    @Test
    public void swapButtonGrowsOnTallCards() {
        float density = context.getResources().getDisplayMetrics().density;
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(300, Math.round(600 * density)));
        activity.setContentView(root);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        view.findViewsWithText(found, context.getString(R.string.glass_limits_title), View.FIND_VIEWS_WITH_TEXT);
        View title = found.get(0);
        View btn = view.getSwapButton();
        assertEquals(Math.round(36 * density), btn.getHeight());
        assertEquals(view.getPaddingTop(), topInCard(title));
        assertEquals(topInCard(title) + title.getHeight() / 2f, topInCard(btn) + btn.getHeight() / 2f, 1f);
    }

    @Test
    public void headerButtonSizeFollowsCardHeight() {
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(28 * density), GlassDimensions.headerButtonPx(context, Math.round(200 * density)));
        assertEquals(Math.round(33 * density), GlassDimensions.headerButtonPx(context, Math.round(300 * density)));
        assertEquals(Math.round(36 * density), GlassDimensions.headerButtonPx(context, Math.round(600 * density)));
    }

    @Test
    public void swapIconFillsMostOfTheButton() {
        View btn = view.getSwapButton();
        assertTrue(btn.getPaddingLeft() <= btn.getLayoutParams().width * 0.1f + 0.5f);
    }

    private int visibleTexts(String text) {
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        view.findViewsWithText(found, text, View.FIND_VIEWS_WITH_TEXT);
        int n = 0;
        for (View v : found) if (shownInCard(v)) n++;
        return n;
    }

    private boolean shownInCard(View v) {
        for (View cur = v; cur != view; cur = (View) cur.getParent()) {
            if (cur.getVisibility() != View.VISIBLE) return false;
        }
        return true;
    }

    private int topInCard(View v) {
        int top = 0;
        for (View cur = v; cur != view; cur = (View) cur.getParent()) top += cur.getTop();
        return top;
    }
}
