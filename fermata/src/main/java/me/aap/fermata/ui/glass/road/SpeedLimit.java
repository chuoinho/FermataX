package me.aap.fermata.ui.glass.road;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public final class SpeedLimit {
    public static final SpeedLimit UNLIMITED = new SpeedLimit(0, false, true);

    public final int value;
    public final boolean mph;
    public final boolean unlimited;

    private SpeedLimit(int value, boolean mph, boolean unlimited) {
        this.value = value;
        this.mph = mph;
        this.unlimited = unlimited;
    }

    public static SpeedLimit kmh(int value) {
        return new SpeedLimit(value, false, false);
    }

    public static SpeedLimit mph(int value) {
        return new SpeedLimit(value, true, false);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof SpeedLimit)) return false;
        SpeedLimit that = (SpeedLimit) o;
        return value == that.value && mph == that.mph && unlimited == that.unlimited;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mph, unlimited);
    }

    @NonNull
    @Override
    public String toString() {
        if (unlimited) return "SpeedLimit{UNLIMITED}";
        return "SpeedLimit{" + value + (mph ? " mph" : " km/h") + "}";
    }
}
