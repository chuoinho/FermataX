package me.app.fermatax.auto.carapps.server;

import android.os.Handler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The app cannot signal a shell-uid server, and its process (with the binder it was given) is usually gone between two
 * car sessions. So it writes this file in its own external files dir, which shell can read, and the persistent
 * (ADB-started) server hands its binder over again when the file changes: no permission, no socket. It is one stat every
 * POLL_MS on the uptime clock, which stops in deep sleep, and the app is only called right after it wrote the file.
 */
public final class AppPing {
	public static final String FILE = "carapps.ping";
	/** Its content when Phone Apps was turned off: the server exits instead of handing its binder over. */
	public static final String STOP = "stop";
	static final long POLL_MS = 2000;
	private final File file;
	private long seen;

	/** A ping from before the start is the start's own: its handoff answers it. */
	AppPing(File file) {
		this.file = file;
		seen = file.lastModified();
	}

	/** shortcut: the user's primary storage path, as Context.getExternalFilesDir gives it; an adopted storage is not seen. */
	static File fileFor(int appUid, String pkg) {
		return new File("/storage/emulated/" + (appUid / 100000) + "/Android/data/" + pkg + "/files/" + FILE);
	}

	/** True once per change of the file; a missing one (never written, app data cleared) asks for nothing. */
	boolean pinged() {
		long m = file.lastModified();
		if (m == seen) return false;
		seen = m;
		return m != 0;
	}

	/** Runs handoff, or stop for a STOP ping, on the handler's thread at each ping; they must not throw. */
	void watch(Handler handler, Runnable handoff, Runnable stop) {
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				check(handoff, stop);
				handler.postDelayed(this, POLL_MS);
			}
		}, POLL_MS);
	}

	void check(Runnable handoff, Runnable stop) {
		if (pinged()) (stopRequested() ? stop : handoff).run();
	}

	private boolean stopRequested() {
		try {
			return STOP.equals(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		} catch (IOException e) {
			return false; // unreadable: the old meaning, hand the binder over
		}
	}
}
