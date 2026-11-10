package me.app.fermatax.auto.carapps.server;

import android.os.Handler;

import java.io.File;

/**
 * The app cannot signal a shell-uid server, and its process (with the binder it was given) is usually gone between two
 * car sessions. So it writes this file in its own external files dir, which shell can read, and the persistent
 * (ADB-started) server hands its binder over again when the file changes: no permission, no socket. It is one stat every
 * POLL_MS on the uptime clock, which stops in deep sleep, and the app is only called right after it wrote the file.
 */
public final class AppPing {
	public static final String FILE = "carapps.ping";
	/** Written when Phone Apps is turned off: the server exits. Seen by its time only, so no content is ever read. */
	public static final String STOP_FILE = "carapps.stop";
	static final long POLL_MS = 2000;
	private final File file;
	private final File stopFile;
	private long seen;
	private long seenStop;

	/**
	 * Taken before the startup handoff: a ping from before it is the start's own (its handoff answers it), while a stop
	 * the app writes during that handoff (it is turned off) is newer and ends the server.
	 */
	AppPing(File file) {
		this.file = file;
		stopFile = new File(file.getParentFile(), STOP_FILE);
		seen = file.lastModified();
		seenStop = stopFile.lastModified();
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

	private boolean stopped() {
		long m = stopFile.lastModified();
		if (m == seenStop) return false;
		seenStop = m;
		return m != 0;
	}

	/** Runs handoff at each ping, or stop once the stop file changed, on the handler's thread; they must not throw. */
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
		if (stopped()) stop.run();
		else if (pinged()) handoff.run();
	}
}
