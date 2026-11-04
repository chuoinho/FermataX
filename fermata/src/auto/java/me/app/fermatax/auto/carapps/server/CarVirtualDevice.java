package me.app.fermatax.auto.carapps.server;

import android.content.AttributionSource;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.Duration;

import me.app.fermatax.auto.carapps.CallerPolicy;
import me.app.fermatax.auto.carapps.ShellAssociation;
import me.app.fermatax.auto.carapps.VirtualDisplayFlags;

/**
 * The car display inside a VirtualDevice, the only kind with its own power group on Android 16: the phone dims,
 * sleeps and locks on its own while the apps keep running, and their keep-screen-on windows hold only this display.
 * Needs uid shell, which holds CREATE_VIRTUAL_DEVICE and can create the association; root fails the package check.
 */
final class CarVirtualDevice {
	private static final String TAG = "CarVirtualDevice";
	/** Locally administered, so no real device has it. */
	private static final String MAC = "02:00:00:00:FE:01";
	private static final String PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING";
	private static final int LOCK_STATE_ALWAYS_UNLOCKED = 1;
	private static final Duration NEVER = Duration.ofDays(365000);

	private final Object device;
	private final VirtualDisplay display;

	private CarVirtualDevice(Object device, VirtualDisplay display) {
		this.device = device;
		this.display = display;
	}

	/** Null if this phone cannot do it: the caller falls back to a plain display sharing the phone's power group. */
	@Nullable
	static CarVirtualDevice create(String name, int w, int h, int dpi, @Nullable Surface s, int flags) {
		if (!VirtualDisplayFlags.usesVirtualDevice(Build.VERSION.SDK_INT) || (Process.myUid() != CallerPolicy.UID_SHELL)) {
			return null;
		}
		try {
			Object device = createDevice(associationId());
			try {
				return new CarVirtualDevice(device, createDisplay(device, name, w, h, dpi, s, flags));
			} catch (Throwable t) {
				closeDevice(device);
				throw t;
			}
		} catch (Throwable t) {
			Log.w(TAG, "VirtualDevice not available, using a plain display", t);
			return null;
		}
	}

	VirtualDisplay display() {
		return display;
	}

	void close() {
		try {
			display.release();
		} catch (Throwable ignored) {
		}
		closeDevice(device);
	}

	private static void closeDevice(Object device) {
		try {
			device.getClass().getMethod("close").invoke(device);
		} catch (Throwable t) {
			Log.w(TAG, "Failed to close VirtualDevice", t);
		}
	}

	private static int associationId() throws Exception {
		Integer id = ShellAssociation.findId(run("cmd", "companiondevice", "list", "0"), MAC);
		if (id != null) return id;
		run("cmd", "companiondevice", "associate", "0", ShellAssociation.PACKAGE, MAC, PROFILE);
		id = ShellAssociation.findId(run("cmd", "companiondevice", "list", "0"), MAC);
		if (id == null) throw new IllegalStateException("Association was not created");
		return id;
	}

	private static String run(String... cmd) throws Exception {
		java.lang.Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (InputStream in = p.getInputStream()) {
			byte[] buf = new byte[1024];
			for (int n = in.read(buf); n >= 0; n = in.read(buf)) out.write(buf, 0, n);
		}
		p.waitFor();
		return out.toString();
	}

	private static Object createDevice(int association) throws Exception {
		IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
				.getMethod("getService", String.class).invoke(null, "virtualdevice");
		Object manager = asInterface("android.companion.virtual.IVirtualDeviceManager", service);

		Class<?> builderClass = Class.forName("android.companion.virtual.VirtualDeviceParams$Builder");
		Object builder = builderClass.getConstructor().newInstance();
		builderClass.getMethod("setLockState", int.class).invoke(builder, LOCK_STATE_ALWAYS_UNLOCKED);
		builderClass.getMethod("setDimDuration", Duration.class).invoke(builder, Duration.ZERO);
		builderClass.getMethod("setScreenOffTimeout", Duration.class).invoke(builder, NEVER);
		Object params = builderClass.getMethod("build").invoke(builder);

		AttributionSource source = new AttributionSource.Builder(Process.myUid())
				.setPid(Process.myPid()).setPackageName(ShellAssociation.PACKAGE).build();

		Method create = method(manager, "createVirtualDevice");
		Class<?>[] types = create.getParameterTypes();
		Object[] args = new Object[types.length];
		for (int i = 0; i < args.length; i++) {
			String type = types[i].getName();
			if (type.equals("android.os.IBinder")) args[i] = new Binder();
			else if (type.equals(AttributionSource.class.getName())) args[i] = source;
			else if (type.equals("int")) args[i] = association;
			else if (type.endsWith("VirtualDeviceParams")) args[i] = params;
			else if (type.endsWith("Listener")) args[i] = asInterface(type, new Binder());
			else throw new IllegalStateException("Unknown argument " + type);
		}
		return create.invoke(manager, args);
	}

	private static VirtualDisplay createDisplay(Object device, String name, int w, int h, int dpi,
																							@Nullable Surface s, int flags) throws Exception {
		Class<?> builderClass = Class.forName("android.hardware.display.VirtualDisplayConfig$Builder");
		Object builder = builderClass.getConstructor(String.class, int.class, int.class, int.class)
				.newInstance(name, w, h, dpi);
		builderClass.getMethod("setFlags", int.class).invoke(builder, flags);
		builderClass.getMethod("setSurface", Surface.class).invoke(builder, s);
		Object config = builderClass.getMethod("build").invoke(builder);

		Object callback = asInterface("android.hardware.display.IVirtualDisplayCallback", new Binder());
		int id = (int) method(device, "createVirtualDisplay").invoke(device, config, callback);
		Object global = Class.forName("android.hardware.display.DisplayManagerGlobal")
				.getMethod("getInstance").invoke(null);
		return (VirtualDisplay) method(global, "createVirtualDisplayWrapper").invoke(global, config, callback, id);
	}

	private static Object asInterface(String aidl, IBinder binder) throws Exception {
		return Class.forName(aidl + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
	}

	private static Method method(Object target, String name) throws NoSuchMethodException {
		for (Method m : target.getClass().getMethods()) {
			if (m.getName().equals(name)) return m;
		}
		throw new NoSuchMethodException(name);
	}
}
