package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

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

    @Test
    public void withinStartsFromTheNearestTiles() {
        TileId t = TileId.of(21.0267, 105.8995);
        double[] b = t.bbox();
        List<TileId> tiles = TileId.within((b[0] + b[2]) / 2, (b[1] + b[3]) / 2, 50);
        assertEquals(t, tiles.get(0));
        assertTrue(tiles.subList(0, 9).containsAll(t.around()));
    }
}
