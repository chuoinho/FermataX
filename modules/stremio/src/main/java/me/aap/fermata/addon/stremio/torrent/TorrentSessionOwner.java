package me.aap.fermata.addon.stremio.torrent;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.frostwire.jlibtorrent.AddTorrentParams;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.alerts.SaveResumeDataAlert;

/** Owns the native torrent session and its handle-to-resume-file leases. */
final class TorrentSessionOwner implements AutoCloseable {
	private final Object lock = new Object();
	private final Map<String, File> resumeFiles = new LinkedHashMap<>();
	private SessionManager session;
	private TorrentAlertRouter alerts;

	SessionManager session() {
		synchronized (lock) {
			if (session == null) {
				session = new SessionManager();
				alerts = new TorrentAlertRouter(session);
				alerts.observeGlobal(alert -> {
					if (alert instanceof SaveResumeDataAlert saved) persistResume(saved);
				});
				session.start();
				if (!session.isDhtRunning()) session.startDht();
				session.maxActiveDownloads(2);
				session.maxActiveSeeds(0);
			}
			return session;
		}
	}

	TorrentAlertRouter alerts() {
		session();
		synchronized (lock) {
			return alerts;
		}
	}

	void registerResume(TorrentHandle handle, File resumeFile) {
		synchronized (resumeFiles) {
			resumeFiles.put(resumeKey(handle), resumeFile);
		}
	}

	void unregisterResume(TorrentHandle handle) {
		synchronized (resumeFiles) {
			resumeFiles.remove(resumeKey(handle));
		}
	}

	void unregisterResume(String key) {
		int separator = key.indexOf(':');
		if (separator > 0) key = key.substring(0, separator);
		synchronized (resumeFiles) {
			resumeFiles.remove(key);
		}
	}

	void remove(TorrentHandle handle) {
		if (!handle.isValid()) return;
		handle.pause();
		synchronized (lock) {
			if ((session != null) && handle.isValid()) session.remove(handle);
		}
	}

	private void persistResume(SaveResumeDataAlert alert) {
		File target;
		synchronized (resumeFiles) {
			target = resumeFiles.get(resumeKey(alert.handle()));
		}
		if (target == null) return;
		try {
			byte[] data = AddTorrentParams.writeResumeData(alert.params()).bencode();
			writeAtomically(target, data);
		} catch (RuntimeException | IOException ignored) {
			// Missing resume data falls back to a verified file recheck on the next run.
		}
	}

	static void writeAtomically(File target, byte[] data) throws IOException {
		File parent = target.getParentFile();
		if (parent == null) throw new IOException("Resume data has no parent directory");
		ensureDirectory(parent);
		File temporary = new File(parent, target.getName() + ".tmp");
		Files.write(temporary.toPath(), data);
		try {
			Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void ensureDirectory(File directory) throws IOException {
		if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
			throw new IOException("Unable to create Stremio torrent cache");
		}
	}

	private static String resumeKey(TorrentHandle handle) {
		return handle.infoHash().toHex().toLowerCase(Locale.ROOT);
	}

	@Override
	public void close() {
		synchronized (lock) {
			if (session != null) {
				alerts.close();
				alerts = null;
				session.stop();
				session = null;
			}
		}
		synchronized (resumeFiles) {
			resumeFiles.clear();
		}
	}
}
