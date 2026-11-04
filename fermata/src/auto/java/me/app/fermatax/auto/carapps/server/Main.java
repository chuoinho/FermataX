package me.app.fermatax.auto.carapps.server;

import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import me.app.fermatax.auto.carapps.ICarDisplayServer;
import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * Entry point for the CarDisplayServer process invoked via app_process.
 */
@SuppressWarnings("deprecation")
public class Main {

	public static void main(String[] args) {
		if (args.length > 0 && "--selftest".equals(args[0])) {
			runSelftest();
			return;
		}

		if (args.length < 3) {
			System.err.println("Usage: app_process / me.app.fermatax.auto.carapps.server.Main <appUid> <versionCode> <appPackage> | --selftest");
			System.exit(1);
			return;
		}

		CarDisplayServerImpl server = null;
		try {
			int appUid = Integer.parseInt(args[0]);
			int versionCode = Integer.parseInt(args[1]);
			String appPackage = args[2];

			if (Looper.myLooper() == null) {
				Looper.prepareMainLooper();
			}

			ServerLifecycle.killStale(Process.myPid());
			Hidden.init();

			server = new CarDisplayServerImpl(appUid, versionCode, appPackage);
			System.out.println("SERVER_READY uid=" + appUid + " version=" + versionCode + " pkg=" + appPackage);

			handoffBinderToApp(appPackage, appUid, server);

			Looper.loop();
		} catch (Throwable t) {
			t.printStackTrace();
			if (server != null) {
				try {
					server.releaseInternal();
				} catch (Throwable ignored) {
				}
			}
			System.exit(2);
		}
	}

	private static void handoffBinderToApp(String appPackage, int appUid, IBinder serverBinder) {
		String authority = appPackage + ".cardisplay";
		int userId = appUid / 100000;
		IBinder token = new Binder();

		try {
			Class<?> amClass = Class.forName("android.app.ActivityManager");
			Method getServiceMethod;
			try {
				getServiceMethod = amClass.getMethod("getService");
			} catch (NoSuchMethodException e) {
				getServiceMethod = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault");
			}
			Object am = getServiceMethod.invoke(null);
			if (am == null) {
				System.err.println("ERROR: ActivityManager service is null");
				System.exit(3);
				return;
			}

			Method getContentProviderExternalMethod = null;
			for (Method m : am.getClass().getMethods()) {
				if ("getContentProviderExternal".equals(m.getName())) {
					getContentProviderExternalMethod = m;
					break;
				}
			}

			if (getContentProviderExternalMethod == null) {
				System.err.println("ERROR: getContentProviderExternal method not found");
				System.exit(3);
				return;
			}

			Object holder;
			if (getContentProviderExternalMethod.getParameterCount() == 4) {
				holder = getContentProviderExternalMethod.invoke(am, authority, userId, token, null);
			} else {
				holder = getContentProviderExternalMethod.invoke(am, authority, userId, token);
			}

			if (holder == null) {
				System.err.println("ERROR: getContentProviderExternal returned null for authority " + authority);
				System.exit(3);
				return;
			}

			Field providerField = holder.getClass().getDeclaredField("provider");
			providerField.setAccessible(true);
			Object provider = providerField.get(holder);
			if (provider == null) {
				System.err.println("ERROR: provider is null in ContentProviderHolder");
				System.exit(3);
				return;
			}

			Bundle extras = new Bundle();
			extras.putBinder("binder", serverBinder);

			Method callMethod = null;
			for (Method m : provider.getClass().getMethods()) {
				if (!"call".equals(m.getName())) continue;
				Class<?>[] types = m.getParameterTypes();
				if (Build.VERSION.SDK_INT >= 31 && types.length > 0 && types[0].getName().contains("AttributionSource")) {
					callMethod = m;
					break;
				}
				if (types.length >= 3) {
					callMethod = m;
				}
			}

			if (callMethod == null) {
				System.err.println("ERROR: call method not found on IContentProvider");
				System.exit(3);
				return;
			}

			Class<?>[] pTypes = callMethod.getParameterTypes();
			Bundle result = null;
			if (pTypes.length == 5 && pTypes[0].getName().contains("AttributionSource")) {
				Object attrSource = null;
				if (Build.VERSION.SDK_INT >= 31) {
					attrSource = new android.content.AttributionSource.Builder(Process.myUid()).build();
				}
				result = (Bundle) callMethod.invoke(provider, attrSource, authority, "attach", null, extras);
			} else if (pTypes.length == 6) {
				result = (Bundle) callMethod.invoke(provider, appPackage, null, authority, "attach", null, extras);
			} else if (pTypes.length == 5) {
				result = (Bundle) callMethod.invoke(provider, appPackage, authority, "attach", null, extras);
			} else if (pTypes.length == 4) {
				result = (Bundle) callMethod.invoke(provider, appPackage, "attach", null, extras);
			} else if (pTypes.length == 3) {
				result = (Bundle) callMethod.invoke(provider, "attach", null, extras);
			}

			System.out.println("BINDER_HANDOFF_SUCCESS authority=" + authority + " ok=" + (result != null && result.getBoolean("ok")));

			try {
				Method removeMethod = am.getClass().getMethod("removeContentProviderExternal", String.class, IBinder.class);
				removeMethod.invoke(am, authority, token);
			} catch (Throwable ignored) {
			}
		} catch (Throwable t) {
			t.printStackTrace();
			System.err.println("ERROR: Binder handoff failed via getContentProviderExternal");
			System.exit(3);
		}
	}

	private static void runSelftest() {
		try {
			if (Looper.myLooper() == null) {
				Looper.prepareMainLooper();
			}

			Hidden.init();
			CarDisplayServerImpl server = new CarDisplayServerImpl(android.os.Process.myUid(), 1, "me.app.fermataX.auto.debug");

			int caps = server.capabilities();
			System.out.println("CAPABILITIES=0x" + Integer.toHexString(caps));

			ImageReader ir = ImageReader.newInstance(1280, 720, PixelFormat.RGBA_8888, 2);
			int id = server.attach(ir.getSurface(), 1280, 720, 160, new Binder());
			System.out.println("ATTACHED displayId=" + id);
			if (id < 0) throw new IllegalStateException("attach failed");

			int sdkFlags = VirtualDisplayFlags.forSdk(Build.VERSION.SDK_INT);
			System.out.println("DISPLAY_FLAGS=0x" + Integer.toHexString(sdkFlags));
			android.view.Display created = Hidden.getDisplayManager().getDisplay(id);
			System.out.println("REPORTED_DISPLAY_FLAGS=0x" + (created == null ? "null" : Integer.toHexString(created.getFlags())));
			int capsAfter = server.capabilities();
			System.out.println("CAPABILITIES_AFTER_ATTACH=0x" + Integer.toHexString(capsAfter));
			System.out.println("HAS_CAP_TASKS=" + ((capsAfter & ICarDisplayServer.CAP_TASKS) != 0));
			System.out.println("HAS_CAP_FOCUS=" + ((capsAfter & ICarDisplayServer.CAP_FOCUS) != 0));
			System.out.println("INJECT_SELFTEST=" + ((capsAfter & ICarDisplayServer.CAP_INJECT) != 0));

			// Verify display in dumpsys
			boolean dumpsysConfirmed = false;
			try {
				java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(Runtime.getRuntime().exec(new String[]{"dumpsys", "display"}).getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.contains("fermatax-car") && line.contains("FLAG_TRUSTED")) {
						dumpsysConfirmed = true;
						break;
					}
				}
				reader.close();
			} catch (Throwable t) {
				dumpsysConfirmed = false;
			}
			System.out.println("DUMPSYS_DISPLAY_CONFIRMED=" + dumpsysConfirmed);

			boolean imeOk = Hidden.setDisplayImePolicy(id, 0);
			System.out.println("IME_POLICY_OK=" + imeOk);

			long now = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 640f, 360f, 0);
			server.inject(down);
			down.recycle();

			MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, 640f, 360f, 0);
			server.inject(up);
			up.recycle();
			System.out.println("INJECT_TOUCH_OK=true");

			server.injectKey(KeyEvent.KEYCODE_BACK);
			System.out.println("INJECT_KEY_OK=true");

			server.release();
			ir.close();
			System.out.println("RELEASE_OK=true");

			System.out.println("SELFTEST_SUCCESS displayId=" + id);
			System.exit(0);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}
}
