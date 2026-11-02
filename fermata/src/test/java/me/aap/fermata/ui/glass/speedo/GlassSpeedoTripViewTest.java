package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassSpeedoTripViewTest {

    private final Context context = RuntimeEnvironment.getApplication();

    @Test
    public void arcStaysInsideTheCardAtEverySize() {
        // Phone, 800x480 car screen, Full HD car screen, tall portrait card.
        float[][] sizesDp = {{180, 200}, {220, 300}, {330, 450}, {300, 700}};
        for (float[] s : sizesDp) {
            RectF arc = new RectF();
            float r = GlassSpeedoTripView.arcBounds(s[0], s[1], 1f, arc);
            float top = arc.top;
            // The 240° arc is open at the bottom: its lowest points sit at sin(30°) below the centre.
            float bottom = arc.centerY() + r * 0.5f;
            String size = s[0] + "x" + s[1];
            assertTrue(size + " top " + top, top >= 38f - 0.01f);
            assertTrue(size + " bottom " + bottom, bottom <= s[1] - 46f + 0.01f);
            assertTrue(size + " sides", arc.left >= 0f && arc.right <= s[0]);
        }
    }

    @Test
    public void speedDigitsGrowWithTheArc() {
        float small = GlassSpeedoTripView.speedTextSp(70f);
        float medium = GlassSpeedoTripView.speedTextSp(100f);
        float large = GlassSpeedoTripView.speedTextSp(155f);
        assertEquals(36f, small, 0.01f);
        assertTrue(medium > small && large > medium);
        assertEquals(medium / 100f, large / 155f, 0.01f);
    }

    @Test
    public void exposesTheSpeedItShows() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        view.updateSpeed(47.4f, null, true);
        assertEquals("47", view.getSpeedText().toString());
        assertEquals("KM/H", view.getSpeedUnitText().toString());
    }

    @Test
    public void swapIconFillsMostOfTheButton() {
        View btn = new GlassSpeedoTripView(context).getSwapButton();
        assertTrue(btn.getPaddingLeft() <= btn.getLayoutParams().width * 0.1f + 0.5f);
    }
}
