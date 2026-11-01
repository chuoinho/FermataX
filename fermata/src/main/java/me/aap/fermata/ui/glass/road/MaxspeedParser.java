package me.aap.fermata.ui.glass.road;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaxspeedParser {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^([1-9]\\d*)$");
    private static final Pattern MPH_PATTERN = Pattern.compile("^([1-9]\\d*)\\s*mph$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, SpeedLimit> IMPLICIT_CODES = new HashMap<>();

    static {
        // AT
        IMPLICIT_CODES.put("AT:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("AT:rural", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("AT:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("AT:motorway", SpeedLimit.kmh(130));

        // BE
        IMPLICIT_CODES.put("BE:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("BE:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("BE:trunk", SpeedLimit.kmh(120));
        IMPLICIT_CODES.put("BE:motorway", SpeedLimit.kmh(120));

        // CH
        IMPLICIT_CODES.put("CH:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("CH:rural", SpeedLimit.kmh(80));
        IMPLICIT_CODES.put("CH:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("CH:motorway", SpeedLimit.kmh(120));

        // CZ
        IMPLICIT_CODES.put("CZ:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("CZ:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("CZ:trunk", SpeedLimit.kmh(110));
        IMPLICIT_CODES.put("CZ:motorway", SpeedLimit.kmh(130));

        // DE
        IMPLICIT_CODES.put("DE:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("DE:rural", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("DE:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("DE:motorway", SpeedLimit.UNLIMITED);

        // DK
        IMPLICIT_CODES.put("DK:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("DK:rural", SpeedLimit.kmh(80));
        IMPLICIT_CODES.put("DK:trunk", SpeedLimit.kmh(80));
        IMPLICIT_CODES.put("DK:motorway", SpeedLimit.kmh(130));

        // ES
        IMPLICIT_CODES.put("ES:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("ES:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("ES:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("ES:motorway", SpeedLimit.kmh(120));

        // FR
        IMPLICIT_CODES.put("FR:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("FR:rural", SpeedLimit.kmh(80));
        IMPLICIT_CODES.put("FR:trunk", SpeedLimit.kmh(110));
        IMPLICIT_CODES.put("FR:motorway", SpeedLimit.kmh(130));

        // GB (mph)
        IMPLICIT_CODES.put("GB:urban", SpeedLimit.mph(30));
        IMPLICIT_CODES.put("GB:rural", SpeedLimit.mph(60));
        IMPLICIT_CODES.put("GB:trunk", SpeedLimit.mph(70));
        IMPLICIT_CODES.put("GB:motorway", SpeedLimit.mph(70));

        // IT
        IMPLICIT_CODES.put("IT:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("IT:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("IT:trunk", SpeedLimit.kmh(110));
        IMPLICIT_CODES.put("IT:motorway", SpeedLimit.kmh(130));

        // NL
        IMPLICIT_CODES.put("NL:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("NL:rural", SpeedLimit.kmh(80));
        IMPLICIT_CODES.put("NL:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("NL:motorway", SpeedLimit.kmh(100));

        // PL
        IMPLICIT_CODES.put("PL:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("PL:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("PL:trunk", SpeedLimit.kmh(100));
        IMPLICIT_CODES.put("PL:motorway", SpeedLimit.kmh(140));

        // RU
        IMPLICIT_CODES.put("RU:urban", SpeedLimit.kmh(60));
        IMPLICIT_CODES.put("RU:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("RU:trunk", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("RU:motorway", SpeedLimit.kmh(110));

        // SE
        IMPLICIT_CODES.put("SE:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("SE:rural", SpeedLimit.kmh(70));
        IMPLICIT_CODES.put("SE:trunk", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("SE:motorway", SpeedLimit.kmh(110));

        // UA
        IMPLICIT_CODES.put("UA:urban", SpeedLimit.kmh(50));
        IMPLICIT_CODES.put("UA:rural", SpeedLimit.kmh(90));
        IMPLICIT_CODES.put("UA:trunk", SpeedLimit.kmh(110));
        IMPLICIT_CODES.put("UA:motorway", SpeedLimit.kmh(130));
    }

    private MaxspeedParser() {}

    @Nullable
    public static SpeedLimit parse(@Nullable String tag) {
        if (tag == null) return null;
        String s = tag.trim();
        if (s.isEmpty()) return null;

        if ("none".equalsIgnoreCase(s)) {
            return SpeedLimit.UNLIMITED;
        }

        Matcher numMatcher = NUMERIC_PATTERN.matcher(s);
        if (numMatcher.matches()) {
            try {
                int val = Integer.parseInt(numMatcher.group(1));
                if (val > 0) return SpeedLimit.kmh(val);
            } catch (NumberFormatException ignored) {}
            return null;
        }

        Matcher mphMatcher = MPH_PATTERN.matcher(s);
        if (mphMatcher.matches()) {
            try {
                int val = Integer.parseInt(mphMatcher.group(1));
                if (val > 0) return SpeedLimit.mph(val);
            } catch (NumberFormatException ignored) {}
            return null;
        }

        return IMPLICIT_CODES.get(s);
    }
}
