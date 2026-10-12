package me.aap.fermata.ui.glass.nav;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model representing a parsed automotive turn-by-turn navigation maneuver.
 */
public class NavTurnInfo {

    public enum TurnDirection {
        STRAIGHT,
        TURN_LEFT,
        TURN_RIGHT,
        SLIGHT_LEFT,
        SLIGHT_RIGHT,
        SHARP_LEFT,
        SHARP_RIGHT,
        U_TURN,
        ROUNDABOUT,
        DESTINATION
    }

    private static final String[] EXIT_KEYWORDS = {
            "exit", "close", "stop", "cancel", "quit", "dừng", "thoát", "kết thúc", "beenden", "quitter"
    };

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\b\\d{1,2}:\\d{2}\\b)");
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\b\\d+(?:[.,]\\d+)?\\s*(?:m|km|mi|ft)\\b)", Pattern.CASE_INSENSITIVE);

    private final boolean active;
    private final TurnDirection direction;
    private final String instruction;
    private final String distanceText;
    private final String eta;
    private final String remainingInfo;
    private final PendingIntent exitIntent;
    private final long timestamp;

    public NavTurnInfo(boolean active, TurnDirection direction, String instruction,
                       String distanceText, String eta, String remainingInfo,
                       PendingIntent exitIntent) {
        this.active = active;
        this.direction = direction != null ? direction : TurnDirection.STRAIGHT;
        this.instruction = instruction != null ? instruction : "";
        this.distanceText = distanceText != null ? distanceText : "";
        this.eta = eta != null ? eta : "";
        this.remainingInfo = remainingInfo != null ? remainingInfo : "";
        this.exitIntent = exitIntent;
        this.timestamp = System.currentTimeMillis();
    }

    public static NavTurnInfo inactive() {
        return new NavTurnInfo(false, TurnDirection.STRAIGHT, "", "", "", "", null);
    }

    public static NavTurnInfo fromNotification(String packageName, @Nullable Bundle extras, @Nullable Notification notification) {
        if (extras == null) {
            return inactive();
        }

        String title = getString(extras, "android.title");
        String text = getString(extras, "android.text");
        String subText = getString(extras, "android.subText");
        String primaryInfo = getString(extras, "android.ongoingActivityNoti.primaryInfo");
        String secondaryInfo = getString(extras, "android.ongoingActivityNoti.secondaryInfo");

        String fullText = (title + " " + text + " " + primaryInfo + " " + secondaryInfo).trim();
        if (fullText.isEmpty()) {
            return inactive();
        }

        TurnDirection direction = inferDirection(fullText);

        // Distance extraction
        String distance = "";
        Matcher distMatcher = DISTANCE_PATTERN.matcher(title);
        if (distMatcher.find()) {
            distance = distMatcher.group(1);
        } else {
            Matcher m2 = DISTANCE_PATTERN.matcher(fullText);
            if (m2.find()) distance = m2.group(1);
        }

        // ETA extraction
        String eta = "";
        if (!subText.isEmpty()) {
            Matcher tm = TIME_PATTERN.matcher(subText);
            if (tm.find()) {
                eta = tm.group(1);
            } else {
                eta = subText;
            }
        }
        if (eta.isEmpty()) {
            Matcher tm = TIME_PATTERN.matcher(text);
            if (tm.find()) eta = tm.group(1);
        }

        PendingIntent exitPi = null;
        if (notification != null && notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                if (action != null && action.title != null && action.actionIntent != null) {
                    String actionTitle = action.title.toString().toLowerCase(Locale.ROOT);
                    for (String kw : EXIT_KEYWORDS) {
                        if (actionTitle.contains(kw)) {
                            exitPi = action.actionIntent;
                            break;
                        }
                    }
                    if (exitPi != null) break;
                }
            }
        }

        return new NavTurnInfo(true, direction, title, distance, eta, text, exitPi);
    }

    public static TurnDirection inferDirection(@NonNull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("u-turn") || lower.contains("quay đầu") || lower.contains("wenden")) {
            return TurnDirection.U_TURN;
        }
        if (lower.contains("roundabout") || lower.contains("bùng binh") || lower.contains("vòng xuyến") || lower.contains("kreisverkehr")) {
            return TurnDirection.ROUNDABOUT;
        }
        if (lower.contains("slight left") || lower.contains("chếch sang trái") || lower.contains("halb links")) {
            return TurnDirection.SLIGHT_LEFT;
        }
        if (lower.contains("slight right") || lower.contains("chếch sang phải") || lower.contains("halb rechts")) {
            return TurnDirection.SLIGHT_RIGHT;
        }
        if (lower.contains("sharp left") || lower.contains("ngoặt trái") || lower.contains("scharf links")) {
            return TurnDirection.SHARP_LEFT;
        }
        if (lower.contains("sharp right") || lower.contains("ngoặt phải") || lower.contains("scharf rechts")) {
            return TurnDirection.SHARP_RIGHT;
        }
        if (lower.contains("left") || lower.contains("trái") || lower.contains("gauche") || lower.contains("izquierda") || lower.contains("links")) {
            return TurnDirection.TURN_LEFT;
        }
        if (lower.contains("right") || lower.contains("phải") || lower.contains("droite") || lower.contains("derecha") || lower.contains("rechts")) {
            return TurnDirection.TURN_RIGHT;
        }
        if (lower.contains("arrived") || lower.contains("destination") || lower.contains("đến nơi") || lower.contains("ziel")) {
            return TurnDirection.DESTINATION;
        }
        return TurnDirection.STRAIGHT;
    }

    private static String getString(Bundle bundle, String key) {
        CharSequence cs = bundle.getCharSequence(key);
        return cs != null ? cs.toString() : "";
    }

    public boolean isActive() {
        return active;
    }

    public TurnDirection getDirection() {
        return direction;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getDistanceText() {
        return distanceText;
    }

    public String getEta() {
        return eta;
    }

    public String getRemainingInfo() {
        return remainingInfo;
    }

    public boolean hasExitAction() {
        return exitIntent != null;
    }

    @Nullable
    public PendingIntent getExitIntent() {
        return exitIntent;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
