package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

public class TileIdTest {

    @Test
    public void math() {
        TileId t = TileId.of(48.1374, 11.5755);              // Munich
        double[] b = t.bbox();
        assertTrue(b[0] <= 48.1374 && 48.1374 <= b[2] && b[1] <= 11.5755 && 11.5755 <= b[3]);
        assertEquals(t, TileId.fromKey(t.key()));
        assertEquals(new TileId(t.x + 1, t.y), t.ahead(90f));
        assertEquals(new TileId(t.x, t.y - 1), t.ahead(0f));
        assertEquals(9, t.around().size());
        int n = TileId.within(48.1374, 11.5755, 50).size();
        assertTrue(n > 250 && n < 800);                       // ~5 km tiles, scaled by latitude                       // ~5 km tiles, scaled by latitude
        assertNull(TileId.fromKey("garbage"));
    }
}
