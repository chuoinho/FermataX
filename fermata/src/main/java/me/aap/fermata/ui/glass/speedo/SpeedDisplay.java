package me.aap.fermata.ui.glass.speedo;

/**
 * What the speed card shows. The engine stays honest (-1 when speed is unknown); the card keeps
 * a number on screen while speed is being tracked and reports the signal quality separately.
 */
final class SpeedDisplay {
    // Hysteresis: Doppler noise at a stop cannot leave 0, a car moving off still shows its speed.
    static final float ZERO_ENTER_KMH = 2f;
    static final float ZERO_EXIT_KMH = 3.5f;

    private float last = -1;
    private boolean atZero = true;

    /** Speed to show in km/h, or -1 when there is nothing to measure with (GPS off, no permission, paused). */
    float show(float engineKmh, TripEngine.Signal signal) {
        switch (signal) {
            case STALE, GPS_OFF, PERMISSION, ERROR, PAUSED, BACKGROUND_BLOCKED -> {
                last = -1;
                atZero = true;
                return -1;
            }
            default -> {
            }
        }
        if (Float.isFinite(engineKmh) && engineKmh >= 0) {
            atZero = engineKmh < (atZero ? ZERO_EXIT_KMH : ZERO_ENTER_KMH);
            last = atZero ? 0 : engineKmh;
        } else if (last < 0) {
            last = 0; // still searching: there is no evidence of motion yet
        }
        return last;
    }
}
