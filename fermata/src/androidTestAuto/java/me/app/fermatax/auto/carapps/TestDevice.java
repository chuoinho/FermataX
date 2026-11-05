package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

	TestDevice(Instrumentation instrumentation) {
		this.instrumentation = instrumentation;
		ctx = instrumentation.getTargetContext().getApplicationContext();
	}

	static boolean isEmulator() {
		return Build.HARDWARE.startsWith("ranchu");
	}

	/**
	 * As the shell uid. executeShellCommand splits on whitespace and parses no quotes or operators, and on Android 11
	 * shell cannot read a script in the app's external cache, so the commands travel as one base64 word.
	 */
	synchronized String shell(String commands) {
		Log.i("FMXTEST", "shell: " + commands);
		String encoded = Base64.encodeToString(commands.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
		ParcelFileDescriptor out = instrumentation.getUiAutomation()
				.executeShellCommand("timeout 60 sh -c echo${IFS}" + encoded + "|base64${IFS}-d|sh");
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
		String kill = "pkill -9 -f " + SERVER_MATCH;
		shell(asServerUid(kill));
	}

	/** As the app starts the server: see ServerCommand. Emulator images have a su for shell. */
	void startServer(String command) {
		shell(asServerUid(command));
	}

	private static String asServerUid(String command) {
		return (Build.VERSION.SDK_INT < 33) ? "/system/xbin/su 1000 sh -c '" + command.replace("'", "'\\''") + "'" : command;
	}


	/** Package of the resumed activity on a display, or null. */
	String resumedOn(int displayId) {
		for (String d : shell("dumpsys activity activities").split("(?m)^Display #")) {
			if (d.startsWith(displayId + " ")) {
				// Android 12 and older write mResumedActivity; not ResumedActivity: Settings embeds its panes on a large display, and that line then has none
				Matcher m = Pattern.compile("(?:topResumedActivity=|mResumedActivity: )ActivityRecord\\{[^}]*u0 (\\S+)/").matcher(d);
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
