package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
public class RoadTileTest {

    private String loadResource(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        assertNotNull("Resource not found: " + path, is);
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    @Test
    public void parsesWaysAndSignals() throws JSONException {
        String json = loadResource("/road/munich_tile.json");
        RoadTile tile = RoadTile.parse(json);
        assertNotNull(tile);
        assertEquals(3, tile.ways.size());
        assertEquals(2, tile.signals.size());

        RoadTile.Way w1 = tile.ways.get(0);
        assertEquals(101L, w1.id);
        assertTrue(w1.oneway);
        assertEquals(SpeedLimit.kmh(30), w1.forward);
        assertNull(w1.backward);
        assertEquals(3, w1.nodes.length);
        assertEquals(3, w1.lat.length);
        assertEquals(3, w1.lon.length);
        assertEquals("Main St", w1.name);

        RoadTile.Signal s1 = tile.signals.get(0);
        assertEquals(201L, s1.node);
        assertEquals(1, s1.direction);

        RoadTile.Signal s2 = tile.signals.get(1);
        assertEquals(202L, s2.node);
        assertEquals(0, s2.direction);
    }

    @Test
    public void rejectsNonJson() {
        try {
            RoadTile.parse("<html>");
            fail("Expected JSONException for html");
        } catch (JSONException expected) {}

        try {
            RoadTile.parse("{}");
            fail("Expected JSONException for missing elements");
        } catch (JSONException expected) {}
    }
}
