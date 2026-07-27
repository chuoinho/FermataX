package me.aap.fermata.media.service;

/** Owns one deferred initial seek and rejects first-frame callbacks from replaced playback. */
final class DeferredInitialSeek {
	private Object engine;
	private Object item;
	private long revision = -1L;
	private long position = -1L;

	long prepare(Object engine, Object item, long revision, long position, boolean defer) {
		clear();
		if (!defer || (position <= 0L)) return position;
		this.engine = engine;
		this.item = item;
		this.revision = revision;
		this.position = position;
		return 0L;
	}

	long consume(Object engine, Object item, long revision) {
		if ((this.engine != engine) || (this.item != item) || (this.revision != revision)) {
			return -1L;
		}
		long position = this.position;
		clear();
		return position;
	}

	void clear() {
		engine = null;
		item = null;
		revision = -1L;
		position = -1L;
	}
}
