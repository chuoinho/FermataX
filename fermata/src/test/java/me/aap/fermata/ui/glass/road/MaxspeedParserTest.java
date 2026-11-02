package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

public class MaxspeedParserTest {

    @Test
    public void parses() {
        assertEquals(SpeedLimit.kmh(50), MaxspeedParser.parse("50"));
        assertEquals(SpeedLimit.mph(30), MaxspeedParser.parse("30 mph"));
        assertEquals(SpeedLimit.kmh(50), MaxspeedParser.parse("DE:urban"));
        assertEquals(SpeedLimit.kmh(100), MaxspeedParser.parse("DE:rural"));
        assertEquals(SpeedLimit.kmh(80), MaxspeedParser.parse("FR:rural"));
        assertEquals(SpeedLimit.kmh(110), MaxspeedParser.parse("RU:motorway"));
        assertSame(SpeedLimit.UNLIMITED, MaxspeedParser.parse("none"));
    }

    @Test
    public void rejects() {
        for (String s : new String[]{null, "", "walk", "signals", "10 knots", "50;30", "XX:urban", "abc", "0", "-5"})
            assertNull(s, MaxspeedParser.parse(s));
    }
}
