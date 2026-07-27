package me.aap.fermata.addon.stremio.torrent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;

/** One native alert listener that fans events out by immutable info-hash identity. */
final class TorrentAlertRouter implements AutoCloseable {
	private static final int[] TYPES = {
			AlertType.ADD_TORRENT.swig(), AlertType.BLOCK_FINISHED.swig(),
			AlertType.PIECE_FINISHED.swig(), AlertType.PEER_CONNECT.swig(),
			AlertType.PEER_DISCONNECTED.swig(), AlertType.TORRENT_CHECKED.swig(),
			AlertType.STATE_CHANGED.swig(), AlertType.TORRENT_ERROR.swig(),
			AlertType.FILE_ERROR.swig(), AlertType.TORRENT_REMOVED.swig(),
			AlertType.SAVE_RESUME_DATA.swig(), AlertType.SAVE_RESUME_DATA_FAILED.swig()
	};

	private final SessionManager session;
	private final Map<String, Set<Consumer<Alert<?>>>> routes = new LinkedHashMap<>();
	private final Set<Consumer<Alert<?>>> global = new LinkedHashSet<>();
	private final AlertListener listener = new AlertListener() {
		@Override
		public int[] types() {
			return TYPES;
		}

		@Override
		public void alert(Alert<?> alert) {
			route(alert);
		}
	};
	private boolean closed;

	TorrentAlertRouter(SessionManager session) {
		this.session = session;
		session.addListener(listener);
	}

	AutoCloseable observe(String infoHash, Consumer<Alert<?>> observer) {
		String key = normalize(infoHash);
		synchronized (this) {
			if (closed) throw new IllegalStateException("Torrent alert router is closed");
			routes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(observer);
		}
		return () -> remove(key, observer);
	}

	AutoCloseable observeGlobal(Consumer<Alert<?>> observer) {
		synchronized (this) {
			if (closed) throw new IllegalStateException("Torrent alert router is closed");
			global.add(observer);
		}
		return () -> {
			synchronized (TorrentAlertRouter.this) {
				global.remove(observer);
			}
		};
	}

	private void route(Alert<?> alert) {
		List<Consumer<Alert<?>>> observers = new ArrayList<>();
		synchronized (this) {
			if (closed) return;
			observers.addAll(global);
			String key = key(alert);
			if (key != null) {
				Set<Consumer<Alert<?>>> targeted = routes.get(key);
				if (targeted != null) observers.addAll(targeted);
			}
		}
		for (Consumer<Alert<?>> observer : observers) {
			try {
				observer.accept(alert);
			} catch (RuntimeException ignored) {
				// One diagnostic observer must not break native alert delivery.
			}
		}
	}

	private synchronized void remove(String key, Consumer<Alert<?>> observer) {
		Set<Consumer<Alert<?>>> observers = routes.get(key);
		if (observers == null) return;
		observers.remove(observer);
		if (observers.isEmpty()) routes.remove(key);
	}

	private static String key(Alert<?> alert) {
		if (!(alert instanceof TorrentAlert<?> torrent)) return null;
		TorrentHandle handle = torrent.handle();
		if ((handle == null) || !handle.isValid()) return null;
		try {
			return normalize(handle.infoHash().toHex());
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	static String normalize(String infoHash) {
		return infoHash.trim().toLowerCase(Locale.ROOT);
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		session.removeListener(listener);
		routes.clear();
		global.clear();
	}
}
