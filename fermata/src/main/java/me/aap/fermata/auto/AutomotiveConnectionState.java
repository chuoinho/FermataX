package me.aap.fermata.auto;

import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local automotive connection state for phone-facing presentation. */
public final class AutomotiveConnectionState {
	private static final AutomotiveConnectionState INSTANCE = new AutomotiveConnectionState();
	private final Set<Listener> listeners = new LinkedHashSet<>();
	private boolean connectionObserved;
	private boolean connected;
	private Object visibleOwner;
	private long connectionEpoch;
	private State state = State.DISCONNECTED;

	AutomotiveConnectionState() {
	}

	public static AutomotiveConnectionState get() {
		return INSTANCE;
	}

	public synchronized State state() {
		return state;
	}

	public synchronized boolean hasConnectionObservation() {
		return connectionObserved;
	}

	public synchronized boolean isProjectionConnected() {
		return connectionObserved && connected;
	}

	public synchronized long connectionEpoch() {
		return connectionEpoch;
	}

	public synchronized void addListener(Listener listener) {
		listeners.add(listener);
	}

	public synchronized void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void connectionChanged(boolean connected) {
		Listener[] notify;
		State next;
		synchronized (this) {
			if (!connectionObserved || (this.connected != connected)) connectionEpoch++;
			else return;
			connectionObserved = true;
			this.connected = connected;
			next = resolveState();
			if (next == state) return;
			state = next;
			notify = listeners.toArray(new Listener[0]);
		}
		notifyListeners(notify, next);
	}

	public void appVisibilityChanged(Object owner, boolean visible) {
		if (owner == null) return;
		Listener[] notify;
		State next;
		synchronized (this) {
			if (visible) visibleOwner = owner;
			else if (visibleOwner == owner) visibleOwner = null;
			else return;
			next = resolveState();
			if (next == state) return;
			state = next;
			notify = listeners.toArray(new Listener[0]);
		}
		notifyListeners(notify, next);
	}

	private State resolveState() {
		return (visibleOwner != null) ? State.APP_VISIBLE :
				connected ? State.CONNECTED : State.DISCONNECTED;
	}

	private static void notifyListeners(Listener[] notify, State next) {
		for (Listener listener : notify) listener.onStateChanged(next);
	}

	public enum State {
		DISCONNECTED,
		CONNECTED,
		APP_VISIBLE
	}

	public interface Listener {
		void onStateChanged(State state);
	}
}
