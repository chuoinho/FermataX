package me.app.fermatax.auto.carapps.server;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.view.InputEvent;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.List;

import me.app.fermatax.auto.carapps.ICarDisplayServer;

/**
 * Encapsulates hidden Android framework APIs accessed via reflection from the root server process.
 * If any reflection fails, the corresponding capability bit is cleared rather than throwing an exception.
 */
@SuppressWarnings("deprecation")
public final class Hidden {

	private static final String TAG = "Hidden";

	private static int capabilities = 0;
	private static Context systemContext;
	private static DisplayManager displayManager;
	private static PowerManager powerManager;

	// Input injection reflection
	private static Object inputManagerInstance;
	private static Method injectInputEventMethod;
	private static Method setDisplayIdMethod;

	// WindowManager IME reflection
	private static Object windowManagerInstance;
	private static Method setDisplayImePolicyMethod;

	// Panel power reflection
	private static Class<?> displayControlClass;
	private static Method getPhysicalDisplayIdsMethod;
	private static Method getPhysicalDisplayTokenMethod;
	private static Method setDisplayPowerModeMethod;

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
			final String pkg = Process.myUid() == 0 ? "root" : "com.android.shell";
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
			powerManager = (PowerManager) systemContext.getSystemService(Context.POWER_SERVICE);
		} catch (Throwable t) {
			systemContext = null;
			powerManager = null;
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

	private static void initPanelControl() {
		try {
			try {
				SurfaceControl.class.getMethod("getPhysicalDisplayIds");
				displayControlClass = SurfaceControl.class;
			} catch (NoSuchMethodException e) {
				Class<?> f = Class.forName("com.android.internal.os.ClassLoaderFactory");
				Method create = f.getDeclaredMethod("createClassLoader", String.class, String.class, String.class,
						ClassLoader.class, int.class, boolean.class, String.class);
				ClassLoader cl = (ClassLoader) create.invoke(null, android.system.Os.getenv("SYSTEMSERVERCLASSPATH"), null, null,
						ClassLoader.getSystemClassLoader(), 0, true, null);
				displayControlClass = cl.loadClass("com.android.server.display.DisplayControl");
				Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
				load.setAccessible(true);
				load.invoke(Runtime.getRuntime(), displayControlClass, "android_servers");
			}

			getPhysicalDisplayIdsMethod = displayControlClass.getMethod("getPhysicalDisplayIds");
			getPhysicalDisplayTokenMethod = displayControlClass.getMethod("getPhysicalDisplayToken", long.class);
			setDisplayPowerModeMethod = SurfaceControl.class.getMethod("setDisplayPowerMode", IBinder.class, int.class);

			if (getPhysicalDisplayIdsMethod != null && getPhysicalDisplayTokenMethod != null && setDisplayPowerModeMethod != null) {
				capabilities |= ICarDisplayServer.CAP_PANEL_POWER;
			}
		} catch (Throwable t) {
			capabilities &= ~ICarDisplayServer.CAP_PANEL_POWER;
		}
	}

	private static void initActivityTaskManager() {
		try {
			IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
					.getMethod("getService", String.class).invoke(null, "activity_task");
			if (b != null) {
				activityTaskManagerInstance = Class.forName("android.app.IActivityTaskManager$Stub")
						.getMethod("asInterface", IBinder.class).invoke(null, b);
				getTasksMethod = activityTaskManagerInstance.getClass()
						.getMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
				removeTaskMethod = activityTaskManagerInstance.getClass()
						.getMethod("removeTask", int.class);
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

	public static PowerManager.WakeLock createWakeLock(String tag) {
		init();
		if (powerManager == null) return null;
		try {
			return powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, tag);
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
		init();
		if (displayControlClass == null || getPhysicalDisplayIdsMethod == null
				|| getPhysicalDisplayTokenMethod == null || setDisplayPowerModeMethod == null) {
			return false;
		}
		try {
			long[] ids = (long[]) getPhysicalDisplayIdsMethod.invoke(null);
			if (ids == null || ids.length == 0) return false;
			for (long id : ids) {
				IBinder token = (IBinder) getPhysicalDisplayTokenMethod.invoke(null, id);
				if (token != null) {
					setDisplayPowerModeMethod.invoke(null, token, mode);
				}
			}
			return true;
		} catch (Throwable t) {
			clearCapability(ICarDisplayServer.CAP_PANEL_POWER);
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	public static ActivityManager.RunningTaskInfo getTopTask(int displayId) {
		init();
		if (activityTaskManagerInstance == null || getTasksMethod == null) return null;
		try {
			List<?> list = (List<?>) getTasksMethod.invoke(activityTaskManagerInstance, 5, false, false, displayId);
			if (list != null && !list.isEmpty()) {
				Object first = list.get(0);
				if (first instanceof ActivityManager.RunningTaskInfo) {
					return (ActivityManager.RunningTaskInfo) first;
				}
			}
		} catch (Throwable ignored) {
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
