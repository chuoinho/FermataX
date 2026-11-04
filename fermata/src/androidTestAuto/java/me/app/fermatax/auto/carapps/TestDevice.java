package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Drives the device for the car tests. The PIN is set only on emulators, never on a real phone. */
final class TestDevice {
	static final String PIN = "1379";
	static final String SERVER_MATCH = "carapps.server.Mai[n]";

	private final Instrumentation instrumentation;
	private final Context ctx;
	private final File script;

	TestDevice(Instrumentation instrumentation) {
		this.instrumentation = instrumentation;
		ctx = instrumentation.getTargetContext().getApplicationContext();
		script = new File(ctx.getExternalCacheDir(), "t.sh"); // the only app files that shell can read
	}

	static boolean isEmulator() {
		return Build.HARDWARE.startsWith("ranchu");
	}

	/** As the shell uid; executeShellCommand itself parses no quotes or operators, so they go in a script. */
	synchronized String shell(String commands) {
		Log.i("FMXTEST", "shell: " + commands);
		try (OutputStream os = new FileOutputStream(script)) {
			os.write((commands + '\n').getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new AssertionError("script: " + commands, e);
		}
		ParcelFileDescriptor out = instrumentation.getUiAutomation()
				.executeShellCommand("timeout 60 sh " + script.getAbsolutePath());
		try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(out)) {
			ByteArrayOutputStream text = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) >= 0; ) text.write(buf, 0, n);
			return text.toString(StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			throw new AssertionError("shell: " + commands, e);
		}
	}

	KeyguardManager keyguard() {
		return ctx.getSystemService(KeyguardManager.class);
	}

	void wakeAndOpen() {
		shell("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; settings put system screen_off_timeout 600000");
		waitUntil("the phone to open", 15_000, () -> !keyguard().isKeyguardLocked());
	}

	void setPin() {
		shell("locksettings set-pin " + PIN);
	}

	void unlockAndClearPin() {
		unlock();
		shell("locksettings clear --old " + PIN);
	}

	void unlock() {
		for (int i = 0; i < 3 && keyguard().isKeyguardLocked(); i++) {
			shell("input keyevent KEYCODE_WAKEUP");
			SystemClock.sleep(2000);
			shell("wm dismiss-keyguard");
			SystemClock.sleep(2000);
			shell("input -d 0 text " + PIN + "; input -d 0 keyevent 66");
			SystemClock.sleep(3000);
		}
		assertFalse("still locked", keyguard().isKeyguardLocked());
	}

	void killServer() {
		shell("pkill -9 -f " + SERVER_MATCH);
	}


	/** Package of the resumed activity on a display, or null. */
	String resumedOn(int displayId) {
		for (String d : shell("dumpsys activity activities").split("(?m)^Display #")) {
			if (d.startsWith(displayId + " ")) {
				// not ResumedActivity: Settings embeds its panes on a large display, and that line then has none
				Matcher m = Pattern.compile("topResumedActivity=ActivityRecord\\{[^}]*u0 (\\S+)/").matcher(d);
				return m.find() ? m.group(1) : null;
			}
		}
		return null;
	}

	static void waitUntil(String what, long ms, BooleanSupplier condition) {
		long end = SystemClock.uptimeMillis() + ms;
		while (!condition.getAsBoolean()) {
			if (SystemClock.uptimeMillis() > end) throw new AssertionError("Timed out waiting for " + what);
			SystemClock.sleep(250);
		}
	}
}
