package me.aap.fermata.acceptance;

import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;

import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Acceptance oracle helpers. New APIs are reached by reflection so that the oracle compiles
 * against the current code and fails at run time until the behaviour exists.
 */
final class Api {
    static final String HUB = "me.aap.fermata.location.LocationHub";

    private Api() {}

    static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            fail("Missing class " + name);
            return null;
        }
    }

    static Method method(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            fail("Missing method " + c.getName() + "." + name);
            return null;
        }
    }

    static Object call(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException r) throw r;
            if (cause instanceof Error err) throw err;
            throw new AssertionError(cause);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static Object staticCall(String cls, String name, Class<?>[] params, Object... args) {
        Class<?> c = cls(cls);
        return call(null, method(c, name, params), args);
    }

    static Object hub(Context ctx) {
        return staticCall(HUB, "get", new Class<?>[]{Context.class}, ctx);
    }

    static void resetHub() {
        staticCall(HUB, "resetForTesting", new Class<?>[0]);
    }

    /** Creates a LocationHub.Listener that forwards to the given consumer. */
    static Object listener(java.util.function.Consumer<Location> consumer) {
        Class<?> l = cls(HUB + "$Listener");
        return Proxy.newProxyInstance(l.getClassLoader(), new Class<?>[]{l}, (p, m, a) -> {
            if (m.getName().equals("onLocation")) {
                consumer.accept((Location) a[0]);
                return null;
            }
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("toString")) return "AcceptanceListener";
            return null;
        });
    }

    static void acquire(Object hub, String tag, java.util.function.Consumer<Location> consumer) {
        Class<?> h = cls(HUB);
        call(hub, method(h, "acquire", String.class, cls(HUB + "$Listener")), tag, listener(consumer));
    }

    static void release(Object hub, String tag) {
        call(hub, method(cls(HUB), "release", String.class), tag);
    }

    static Location latest(Object hub) {
        return (Location) call(hub, method(cls(HUB), "latest"));
    }

    static Location freshSystemLocation(Object hub, long maxAgeMs) {
        return (Location) call(hub, method(cls(HUB), "freshSystemLocation", long.class), maxAgeMs);
    }

    static void resetCarLocator() {
        staticCall("me.aap.fermata.carfinder.CarLocatorManager", "resetForTesting", new Class<?>[0]);
    }

    /** Runs every looper (main and background) until idle, twice to flush cross-thread posts. */
    static void idleAll() {
        for (int i = 0; i < 3; i++) {
            ShadowLooper.idleMainLooper();
            List<Looper> loopers = new java.util.ArrayList<>(ShadowLooper.getAllLoopers());
            for (Looper l : loopers) {
                if (l != Looper.getMainLooper() && l.getThread().isAlive()) shadowOf(l).idle();
            }
        }
    }

    static Location fix(String provider, double lat, double lon, long ageMs) {
        Location l = new Location(provider);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(5f);
        l.setSpeed(0f);
        l.setTime(System.currentTimeMillis() - ageMs);
        l.setElapsedRealtimeNanos((SystemClock.elapsedRealtime() - ageMs) * 1_000_000L);
        return l;
    }
}
