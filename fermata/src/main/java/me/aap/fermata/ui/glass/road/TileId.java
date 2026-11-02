package me.aap.fermata.ui.glass.road;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TileId {
    public static final int Z = 13;
    private static final int N = 1 << Z; // 8192

    public final int x;
    public final int y;

    public TileId(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static TileId of(double lat, double lon) {
        int x = (int) Math.floor((lon + 180.0) / 360.0 * N);
        double latRad = Math.toRadians(lat);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * N);
        return new TileId(x, y);
    }

    public double[] bbox() {
        double west = (double) x / N * 360.0 - 180.0;
        double east = (double) (x + 1) / N * 360.0 - 180.0;
        double north = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * y / N))));
        double south = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * (y + 1) / N))));
        return new double[]{south, west, north, east};
    }

    public TileId ahead(float bearingDeg) {
        float b = (bearingDeg % 360f + 360f) % 360f;
        int sector = Math.round(b / 45f) % 8;
        int dx = 0;
        int dy = 0;
        switch (sector) {
            case 0: dx = 0;  dy = -1; break; // N
            case 1: dx = 1;  dy = -1; break; // NE
            case 2: dx = 1;  dy = 0;  break; // E
            case 3: dx = 1;  dy = 1;  break; // SE
            case 4: dx = 0;  dy = 1;  break; // S
            case 5: dx = -1; dy = 1;  break; // SW
            case 6: dx = -1; dy = 0;  break; // W
            case 7: dx = -1; dy = -1; break; // NW
        }
        return new TileId(x + dx, y + dy);
    }

    public List<TileId> around() {
        List<TileId> list = new ArrayList<>(9);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                list.add(new TileId(x + dx, y + dy));
            }
        }
        return list;
    }

    public static List<TileId> within(double lat, double lon, double radiusKm) {
        List<TileId> result = new ArrayList<>();
        double latDelta = radiusKm / 111.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double lonDelta = radiusKm / (111.0 * (cosLat > 0.01 ? cosLat : 0.01));

        TileId minTile = of(lat + latDelta, lon - lonDelta);
        TileId maxTile = of(lat - latDelta, lon + lonDelta);

        int minX = Math.min(minTile.x, maxTile.x);
        int maxX = Math.max(minTile.x, maxTile.x);
        int minY = Math.min(minTile.y, maxTile.y);
        int maxY = Math.max(minTile.y, maxTile.y);

        for (int ty = minY; ty <= maxY; ty++) {
            for (int tx = minX; tx <= maxX; tx++) {
                double cLon = ((double) tx + 0.5) / N * 360.0 - 180.0;
                double cLat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * ((double) ty + 0.5) / N))));
                if (haversineKm(lat, lon, cLat, cLon) <= radiusKm) {
                    result.add(new TileId(tx, ty));
                }
            }
        }
        return result;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    public String key() {
        return Z + "_" + x + "_" + y;
    }

    @Nullable
    public static TileId fromKey(String key) {
        if (key == null) return null;
        String[] parts = key.split("_");
        if (parts.length != 3) return null;
        try {
            int z = Integer.parseInt(parts[0]);
            if (z != Z) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            return new TileId(x, y);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TileId)) return false;
        TileId tileId = (TileId) o;
        return x == tileId.x && y == tileId.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @NonNull
    @Override
    public String toString() {
        return key();
    }
}
