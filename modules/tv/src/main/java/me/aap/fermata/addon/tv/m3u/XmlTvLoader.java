package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.XmlTvSchema.hasProgrammeIndex;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.database.sqlite.SQLiteDatabase;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.holder.BooleanHolder;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader.Status;

final class XmlTvLoader {
	private final XmlTv owner;
	private final SQLite sql;
	private final XmlTvUpdateScheduler updates = new XmlTvUpdateScheduler();
	private final XmlTvParser parser = new XmlTvParser();

	XmlTvLoader(XmlTv owner, SQLite sql) {
		this.owner = owner;
		this.sql = sql;
	}

	boolean isClosed() {
		return updates.isClosed() || sql.isClosed();
	}

	void close() {
		updates.close();
	}

	FutureSupplier<XmlTv> start(TvM3uItem item, boolean hasIndex) {
		return load(item, hasIndex);
	}

	private FutureSupplier<?> refresh(TvM3uItem item) {
		if (isClosed()) return completedNull();
		return sql.query(database -> !isClosed()
				? load(item, hasProgrammeIndex(database)) : completedNull());
	}

	private FutureSupplier<XmlTv> load(TvM3uItem item, boolean hasIndex) {
		if (isClosed()) return completedNull();
		BooleanHolder noUpdate = new BooleanHolder();
		return item.getResource().downloadEpg().then(status -> {
			if (isClosed()) return completedNull();
			return switch (XmlTvLoadPolicy.resolveDownload(hasIndex, status.bytesDownloaded())) {
				case USE_EXISTING -> {
					Log.i("XMLTV is up to date: ", status.getUrl());
					yield completed(owner);
				}
				case PARSE_AFTER_DELAY -> {
					noUpdate.value = true;
					Log.i("Scheduling XMLTV update in 30 seconds: ", status.getUrl());
					updates.schedule(() -> !isClosed() ? load(item, status) : completedNull(),
							XmlTvLoadPolicy.REPLACEMENT_DELAY_MS);
					yield completed(owner);
				}
				case PARSE_NOW -> load(item, status);
			};
		}).onFailure(error -> {
			if (isClosed()) return;
			String url = item.getResource().getEpgUrl();
			switch (XmlTvLoadPolicy.resolveFailure(hasIndex)) {
				case RETRY -> {
					Log.e(error, "Failed to load XMLTV: ", url, ". Retrying in 5 minutes.");
					updates.schedule(() -> !isClosed() ? refresh(item) : completedNull(),
							XmlTvLoadPolicy.RETRY_DELAY_MS);
				}
				case CLOSE -> {
					Log.e(error, "Failed to load XMLTV: ", url);
					owner.close();
				}
			}
		}).onSuccess(value -> {
			if (isClosed() || noUpdate.value) return;
			TvM3uFile file = item.getResource();
			long time = System.currentTimeMillis();
			long updateTime = XmlTvLoadPolicy.nextUpdateTime(file.getEpgTimeStamp(),
					file.getEpgMaxAge(), time);
			Log.i("Scheduling XMLTV update at ", new Date(updateTime));
			updates.schedule(() -> !isClosed() ? refresh(item) : completedNull(),
					updateTime - System.currentTimeMillis());
		});
	}

	private FutureSupplier<XmlTv> load(TvM3uItem item, Status status) {
		if (isClosed()) return completedNull();
		Map<String, List<TvM3uTrackItem>> byId = new HashMap<>();
		Map<String, List<TvM3uTrackItem>> byName = new HashMap<>();
		return XmlTvChannelMatcher.collect(item, byId, byName, this::isClosed)
				.then(value -> !isClosed()
						? sql.query(database -> loadXml(item, status, database, byId, byName))
						: completedNull());
	}

	private XmlTv loadXml(TvM3uItem item, Status status, SQLiteDatabase database,
										Map<String, List<TvM3uTrackItem>> byId,
										Map<String, List<TvM3uTrackItem>> byName)
			throws ParserConfigurationException, SAXException, IOException {
		Log.i("Loading XMLTV: ", status.getUrl());
		long time = System.currentTimeMillis();
		try (InputStream in = status.getFileStream(true)) {
			parser.parse(in, database, byId, byName, item.getResource().getEpgShift());
		}
		Log.i("XMLTV has been successfully loaded in ", System.currentTimeMillis() - time,
				" milliseconds: ", status.getUrl());
		return owner;
	}
}
