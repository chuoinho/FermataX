package me.aap.fermata.ui.glass.nav;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Android NotificationListenerService capturing turn-by-turn maneuvers from Google Maps, Waze, etc.
 */
public class AutomotiveNavNotificationService extends NotificationListenerService {

    public interface NavListener {
        void onNavTurnUpdated(@NonNull NavTurnInfo info);
    }

    private static final Set<String> NAV_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps",
            "com.waze",
            "com.here.app.maps",
            "net.osmand",
            "com.tomtom.speedcams.android.map",
            "com.sygic.aura"
    ));

    private static final CopyOnWriteArraySet<NavListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile NavTurnInfo currentInfo = NavTurnInfo.inactive();
    private static volatile String activeNavPackage = null;

    public static void registerListener(@NonNull NavListener listener) {
        LISTENERS.add(listener);
        listener.onNavTurnUpdated(currentInfo);
    }

    public static void unregisterListener(@NonNull NavListener listener) {
        LISTENERS.remove(listener);
    }

    @NonNull
    public static NavTurnInfo getCurrentInfo() {
        return currentInfo;
    }

    public static boolean isNotificationAccessGranted(@Nullable Context context) {
        if (context == null) return false;
        try {
            String listeners = Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            return listeners != null && listeners.contains(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        Notification n = sbn.getNotification();
        if (n == null) return;

        boolean isNavCategory = Notification.CATEGORY_NAVIGATION.equals(n.category);
        if (!isNavCategory && !NAV_PACKAGES.contains(pkg)) {
            return;
        }

        NavTurnInfo info = NavTurnInfo.fromNotification(pkg, n.extras, n);
        if (info.isActive()) {
            activeNavPackage = pkg;
            currentInfo = info;
            dispatchUpdate(info);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals(activeNavPackage)) {
            activeNavPackage = null;
            currentInfo = NavTurnInfo.inactive();
            dispatchUpdate(currentInfo);
        }
    }

    private static void dispatchUpdate(NavTurnInfo info) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (NavListener l : LISTENERS) {
                l.onNavTurnUpdated(info);
            }
        } else {
            MAIN_HANDLER.post(() -> {
                for (NavListener l : LISTENERS) {
                    l.onNavTurnUpdated(info);
                }
            });
        }
    }
}
