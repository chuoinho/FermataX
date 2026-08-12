package me.aap.fermata.media.service;

import androidx.annotation.Nullable;

/** Coordinates temporary foreground promotion while a playback attempt acquires audio focus. */
final class PlaybackForegroundCoordinator {
	private final Host host;
	private long generation;
	private boolean foreground;

	PlaybackForegroundCoordinator(Host host) {
		this.host = host;
	}

	@Nullable
	Lease acquire() {
		long token = ++generation;
		boolean promoted = !foreground;
		try {
			if (promoted) host.promote();
			host.retainLifetime();
			foreground = true;
			return new Lease(token, promoted);
		} catch (RuntimeException error) {
			if (promoted) {
				try {
					host.demote();
				} catch (RuntimeException ignored) {
				}
				foreground = false;
			}
			host.failed(error);
			return null;
		}
	}

	void keepForeground() {
		if (foreground) return;
		try {
			host.promote();
			host.retainLifetime();
			foreground = true;
		} catch (RuntimeException error) {
			host.failed(error);
		}
	}

	void leaveForeground(boolean removeNotification) {
		generation++;
		if (!foreground) return;
		foreground = false;
		host.demote(removeNotification);
	}

	final class Lease {
		private final long token;
		private final boolean promoted;
		private boolean terminal;

		private Lease(long token, boolean promoted) {
			this.token = token;
			this.promoted = promoted;
		}

		void commit() {
			terminal = true;
		}

		void rollback() {
			if (terminal) return;
			terminal = true;
			if (!promoted || (token != generation) || !foreground) return;
			foreground = false;
			host.demote(false);
		}
	}

	interface Host {
		void promote();

		void retainLifetime();

		default void demote() {
			demote(false);
		}

		void demote(boolean removeNotification);

		void failed(RuntimeException error);
	}
}
