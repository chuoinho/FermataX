package me.aap.fermata.addon.tv.m3u;

import static java.util.Collections.emptyList;
import static me.aap.fermata.addon.tv.m3u.TvM3uTrackItem.EPG_ID_NOT_FOUND;
import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.hasProgrammeIndex;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import java.io.Closeable;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class XmlTv implements Closeable {
	private final SQLite sql;
	private final XmlTvLoader loader;

	private XmlTv(SQLite sql) {
		this.sql = sql;
		loader = new XmlTvLoader(this, sql);
	}

	public boolean isClosed() {
		return loader.isClosed();
	}

	@Override
	public void close() {
		loader.close();
		sql.close();
	}

	@Override
	protected void finalize() {
		close();
	}

	public static FutureSupplier<XmlTv> create(TvM3uItem item) {
		String url = item.getEpgUrl();
		if (url == null) return completedNull();

		try {
			XmlTv xml = new XmlTv(SQLite.get(item.getResource().getEpgDbFile()));
			return xml.sql.query(database -> {
				boolean hasIndex = hasProgrammeIndex(database);
				return switch (XmlTvLoadPolicy.resolveStartup(hasIndex)) {
					case REFRESH_IN_BACKGROUND -> {
						xml.loader.start(item, true);
						yield completed(xml);
					}
					case WAIT_FOR_INITIAL_LOAD -> xml.loader.start(item, false);
				};
			}).then(value -> value).onFailure(error -> xml.close());
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	public FutureSupplier<Void> update(TvM3uTrackItem track) {
		if (track.getEpgId() == EPG_ID_NOT_FOUND) {
			Log.d("Channel not found - skipping update: ", track);
			return completedVoid();
		}
		if (isClosed()) {
			Log.d("Database is closed: ", sql);
			return completedVoid();
		}

		return sql.execute(database -> {
			try {
				XmlTvDatabase.updateTrack(database, track);
			} catch (Throwable ex) {
				Log.e(ex, "Failed to update channel: ", track.getName());
			}
		});
	}

	public FutureSupplier<List<TvM3uEpgItem>> getEpg(TvM3uTrackItem track) {
		if (isClosed()) return completed(emptyList());
		return sql.query(database -> {
			try {
				return XmlTvDatabase.getEpg(database, track);
			} catch (Throwable ex) {
				Log.e(ex, "Failed to load epg for channel: ", track.getName());
				return emptyList();
			}
		});
	}
}
