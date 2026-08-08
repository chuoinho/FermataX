package me.aap.fermata.addon.cast;

import android.content.Context;

import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.io.Closeable;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.lib.MediaLib;

/**
 * @author Andrey Pavlenko
 */
public class CastMediaEngineProvider implements MediaEngineProvider, Closeable {
	private final CastSession session;
	private final RemoteMediaClient client;
	private final CastServer server;
	private CastMediaEngine engine;
	private boolean closed;

	public CastMediaEngineProvider(CastSession session, RemoteMediaClient client, MediaLib lib) {
		this.session = session;
		this.client = client;
		server = new CastServer(lib);
	}

	@Override
	public void init(Context ctx) {
	}

	@Override
	public MediaEngine createEngine(MediaEngine.Listener listener) {
		if (engine != null) {
			if (engine.listener == listener) return engine;
			engine.close();
		}
		return engine = new CastMediaEngine(server, session, client, listener);
	}

	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;
		server.close();
	}

	public synchronized void shutdownPlayback() {
		CastMediaEngine current = engine;
		engine = null;
		try {
			if (current != null) {
				try {
					current.stop();
				} finally {
					current.close();
				}
			}
		} finally {
			close();
		}
	}
}
