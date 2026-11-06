package me.app.fermatax.auto.carapps.server;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.SurfaceControl;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import me.app.fermatax.auto.carapps.ICarDisplayServer;
import me.app.fermatax.auto.carapps.PanelPowerPolicy;

/**
 * Encapsulates hidden Android framework APIs accessed via reflection from the root server process.
 * If any reflection fails, the corresponding capability bit is cleared rather than throwing an exception.
 */
@SuppressWarnings("deprecation")
public final class Hidden {

	private static final String TAG = "Hidden";
	private static final int MAX_TASKS = 100;

	private static int capabilities = 0;
	private static Context systemContext;
	private static DisplayManager displayManager;

	// Input injection reflection
	private static Object inputManagerInstance;
	private static Method injectInputEventMethod;
	private static Method setDisplayIdMethod;

	// WindowManager IME reflection
	private static Object windowManagerInstance;
	private static Method setDisplayImePolicyMethod;

	// Panel power reflection
	private static Method getPhysicalDisplayIdsMethod;
	private static Method getPhysicalDisplayTokenMethod;
	private static Method setDisplayPowerModeMethod;
	private static volatile boolean panelForcedOff;

	// ActivityTaskManager reflection
	private static Object activityTaskManagerInstance;
	private static Method getTasksMethod;
	private static Method removeTaskMethod;

	private static boolean initialized = false;

	private Hidden() {
	}

	public static synchronized void init() {
		if (initialized) return;
		initialized = true;

		initSystemContext();
		initDisplayManager();
		initInputManager();
		initWindowManager();
		initPanelControl();
		initActivityTaskManager();
	}

	private static void initSystemContext() {
		try {
			if (Looper.myLooper() == null) {
				Looper.prepareMainLooper();
			}
			Class<?> atClass = Class.forName("android.app.ActivityThread");
			Object thread = atClass.getMethod("systemMain").invoke(null);
			Context rawSys = (Context) atClass.getMethod("getSystemContext").invoke(thread);
			final int uid = Process.myUid();
			final String pkg = (uid == 0) ? "root" : (uid == 1000) ? "android" : "com.android.shell";
			systemContext = new ContextWrapper(rawSys) {
				@Override
				public String getPackageName() {
					return pkg;
				}

				@Override
				public String getOpPackageName() {
					return pkg;
				}
			};
		} catch (Throwable t) {
			systemContext = null;
		}
	}

	private static void initDisplayManager() {
		if (systemContext == null) return;
		try {
			displayManager = DisplayManager.class.getDeclaredConstructor(Context.class).newInstance(systemContext);
			if (displayManager != null) {
				capabilities |= ICarDisplayServer.CAP_VD;
				// Default assume CAP_TRUSTED when running as root (cleared if createVirtualDisplay rejects it)
				capabilities |= ICarDisplayServer.CAP_TRUSTED;
				// Assumed the same way: only a created display can tell (cleared by attach)
				if (Build.VERSION.SDK_INT >= 34) capabilities |= ICarDisplayServer.CAP_FOCUS;
			}
		} catch (Throwable t) {
			displayManager = null;
		}
	}

	private static void initInputManager() {
		try {
			IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
					.getMethod("getService", String.class).invoke(null, "input");
			if (b != null) {
				inputManagerInstance = Class.forName("android.hardware.input.IInputManager$Stub")
						.getMethod("asInterface", IBinder.class).invoke(null, b);
				injectInputEventMethod = inputManagerInstance.getClass()
						.getMethod("injectInputEvent", InputEvent.class, int.class);
			}

			setDisplayIdMethod = InputEvent.class.getMethod("setDisplayId", int.class);

			if (inputManagerInstance != null && injectInputEventMethod != null && setDisplayIdMethod != null) {
				capabilities |= ICarDisplayServer.CAP_INJECT;
			}
		} catch (Throwable t) {
			capabilities &= ~ICarDisplayServer.CAP_INJECT;
		}
	}

	private static void initWindowManager() {
		try {
			IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
					.getMethod("getService", String.class).invoke(null, "window");
			if (b != null) {
				windowManagerInstance = Class.forName("android.view.IWindowManager$Stub")
						.getMethod("asInterface", IBinder.class).invoke(null, b);
				setDisplayImePolicyMethod = windowManagerInstance.getClass()
						.getMethod("setDisplayImePolicy", int.class, int.class);
				if (setDisplayImePolicyMethod != null) {
					capabilities |= ICarDisplayServer.CAP_IME;
				}
			}
		} catch (Throwable t) {
			capabilities &= ~ICarDisplayServer.CAP_IME;
		}
	}

	@SuppressLint("BlockedPrivateApi")
	static void initPanelControl() {
		try {
			if (!PanelPowerPolicy.sdkSupported(Build.VERSION.SDK_INT)) {
				capabilities &= ~ICarDisplayServer.CAP_PANEL_POWER;
				return;
			}
			Class<?> control = android.view.SurfaceControl.class;
			try {
				control.getMethod("getPhysicalDisplayIds");
			} catch (NoSuchMethodException e) {
				Class<?> f = Class.forName("com.android.internal.os.ClassLoaderFactory");
				Method create = f.getDeclaredMethod("createClassLoader", String.class, String.class, String.class,
						ClassLoader.class, int.class, boolean.class, String.class);
				ClassLoader cl = (ClassLoader) create.invoke(null, "/system/framework/services.jar", null, null,
						ClassLoader.getSystemClassLoader(), 0, true, null);
				control = cl.loadClass("com.android.server.display.DisplayControl");
				Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
				load.setAccessible(true);
				load.invoke(Runtime.getRuntime(), control, "android_servers");
			}
			bindPanelControl(control, android.view.SurfaceControl.class);
		} catch (Throwable t) {
			capabilities &= ~ICarDisplayServer.CAP_PANEL_POWER;
		}
	}

	static void bindPanelControl(Class<?> control, Class<?> power) throws NoSuchMethodException {
		getPhysicalDisplayIdsMethod = control.getMethod("getPhysicalDisplayIds");
		getPhysicalDisplayTokenMethod = control.getMethod("getPhysicalDisplayToken", long.class);
		setDisplayPowerModeMethod = power.getMethod("setDisplayPowerMode", IBinder.class, int.class);
		capabilities |= ICarDisplayServer.CAP_PANEL_POWER;
	}

	private static void initActivityTaskManager() {
		try {
			IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
					.getMethod("getService", String.class).invoke(null, "activity_task");
			if (b == null) {
				b = (IBinder) Class.forName("android.os.ServiceManager")
						.getMethod("getService", String.class).invoke(null, "activity");
			}
			if (b != null) {
				try {
					activityTaskManagerInstance = Class.forName("android.app.IActivityTaskManager$Stub")
							.getMethod("asInterface", IBinder.class).invoke(null, b);
				} catch (Throwable t) {
					activityTaskManagerInstance = Class.forName("android.app.IActivityManager$Stub")
							.getMethod("asInterface", IBinder.class).invoke(null, b);
				}
				if (activityTaskManagerInstance != null) {
					Class<?> cls = activityTaskManagerInstance.getClass();
					getTasksMethod = findGetTasksMethod(cls);
					try {
						removeTaskMethod = cls.getMethod("removeTask", int.class);
					} catch (NoSuchMethodException ignored) {
					}
					if (getTasksMethod != null && removeTaskMethod != null) {
						capabilities |= ICarDisplayServer.CAP_TASKS;
					}
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public static int getCapabilities() {
		init();
		return capabilities;
	}

	public static boolean hasCapability(int cap) {
		init();
		return (capabilities & cap) == cap;
	}

	public static void clearCapability(int cap) {
		capabilities &= ~cap;
	}

	public static DisplayManager getDisplayManager() {
		init();
		return displayManager;
	}

	public static boolean isPhoneScreenOn() {
		try {
			Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
			Object info = global.getMethod("getDisplayInfo", int.class)
					.invoke(global.getMethod("getInstance").invoke(null), 0);
			return (info != null) && info.getClass().getField("state").getInt(info) == Display.STATE_ON;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 0, the phone's own group, if unknown: the caller then treats the display as sharing it. */
	public static int getDisplayGroupId(int displayId) {
		try {
			Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
			Object info = global.getMethod("getDisplayInfo", int.class)
					.invoke(global.getMethod("getInstance").invoke(null), displayId);
			return (info == null) ? 0 : info.getClass().getField("displayGroupId").getInt(info);
		} catch (Throwable t) {
			return 0;
		}
	}

	/** Holds only the power group of this display on. Null if it could not be held. */
	@SuppressLint("WakelockTimeout") // for the whole car session; the system drops it if this process dies
	@Nullable
	public static PowerManager.WakeLock acquireDisplayWakeLock(int displayId, String tag) {
		init();
		if (systemContext == null) return null;
		try {
			PowerManager pm = (PowerManager) systemContext.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock lock = (PowerManager.WakeLock) PowerManager.class
					.getMethod("newWakeLock", int.class, String.class, int.class)
					.invoke(pm, PowerManager.SCREEN_BRIGHT_WAKE_LOCK, tag, displayId);
			lock.acquire();
			return lock;
		} catch (Throwable t) {
			return null;
		}
	}

	public static boolean injectInputEvent(InputEvent event, int mode) {
		init();
		if (inputManagerInstance == null || injectInputEventMethod == null) return false;
		try {
			Object result = injectInputEventMethod.invoke(inputManagerInstance, event, mode);
			return Boolean.TRUE.equals(result);
		} catch (Throwable t) {
			clearCapability(ICarDisplayServer.CAP_INJECT);
			return false;
		}
	}

	public static boolean setDisplayId(InputEvent event, int displayId) {
		init();
		if (setDisplayIdMethod == null || event == null) return false;
		try {
			setDisplayIdMethod.invoke(event, displayId);
			return true;
		} catch (Throwable t) {
			clearCapability(ICarDisplayServer.CAP_INJECT);
			return false;
		}
	}

	public static boolean setDisplayImePolicy(int displayId, int policy) {
		init();
		if (windowManagerInstance == null || setDisplayImePolicyMethod == null) return false;
		try {
			setDisplayImePolicyMethod.invoke(windowManagerInstance, displayId, policy);
			return true;
		} catch (Throwable t) {
			clearCapability(ICarDisplayServer.CAP_IME);
			return false;
		}
	}

	public static boolean setPhysicalDisplayPowerMode(int mode) {
		if (!hasCapability(ICarDisplayServer.CAP_PANEL_POWER)) return false;
		panelForcedOff = (mode == 0);
		try {
			long[] ids = (long[]) getPhysicalDisplayIdsMethod.invoke(null);
			if (ids == null || ids.length == 0) return false;
			for (long id : ids) {
				IBinder token = (IBinder) getPhysicalDisplayTokenMethod.invoke(null, id);
				if (token != null) setDisplayPowerModeMethod.invoke(null, token, mode);
			}
			return true;
		} catch (Throwable t) {
			Log.e(TAG, "Failed to set physical display power mode " + mode, t);
			clearCapability(ICarDisplayServer.CAP_PANEL_POWER);
			return false;
		}
	}

	/** Only a panel this process turned off: a sleeping phone must not light up when the server exits. */
	public static void restorePanelIfForcedOff() {
		if (panelForcedOff) setPhysicalDisplayPowerMode(2);
	}

	public static ActivityManager.RunningTaskInfo getTopTask(int displayId) {
		init();
		if (activityTaskManagerInstance == null || getTasksMethod == null) return null;
		return topTask(activityTaskManagerInstance, getTasksMethod, displayId);
	}

	/** getTasks(maxNum) on Android 10-11, a 3 argument form on early Android 13, the 4 argument form with a display after. */
	@Nullable
	static Method findGetTasksMethod(Class<?> clazz) {
		if (clazz == null) return null;
		for (Class<?>[] params : new Class<?>[][]{
				{int.class, boolean.class, boolean.class, int.class},
				{int.class, boolean.class, boolean.class},
				{int.class}}) {
			try {
				return clazz.getMethod("getTasks", params);
			} catch (NoSuchMethodException ignored) {
			}
		}
		return null;
	}

	@Nullable
	static ActivityManager.RunningTaskInfo topTask(Object instance, Method getTasks, int displayId) {
		try {
			switch (getTasks.getParameterCount()) {
				case 1:
					return topOnDisplay((List<?>) getTasks.invoke(instance, MAX_TASKS), displayId);
				case 3:
					return topOnDisplay((List<?>) getTasks.invoke(instance, MAX_TASKS, false, false), displayId);
				default:
					List<?> list = (List<?>) getTasks.invoke(instance, 5, false, false, displayId);
					if (list != null && !list.isEmpty() && (list.get(0) instanceof ActivityManager.RunningTaskInfo)) {
						return (ActivityManager.RunningTaskInfo) list.get(0);
					}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** Android 10-11 and early 13 list the tasks of every display, most recent first. */
	@Nullable
	static ActivityManager.RunningTaskInfo topOnDisplay(List<?> tasks, int displayId) throws ReflectiveOperationException {
		Field display = TaskInfo.class.getField("displayId");
		for (Object t : tasks) {
			if ((t instanceof ActivityManager.RunningTaskInfo) && (display.getInt(t) == displayId)) {
				return (ActivityManager.RunningTaskInfo) t;
			}
		}
		return null;
	}

	public static boolean removeTask(int taskId) {
		init();
		if (activityTaskManagerInstance == null || removeTaskMethod == null) return false;
		try {
			Object result = removeTaskMethod.invoke(activityTaskManagerInstance, taskId);
			return Boolean.TRUE.equals(result);
		} catch (Throwable ignored) {
			return false;
		}
	}
}
