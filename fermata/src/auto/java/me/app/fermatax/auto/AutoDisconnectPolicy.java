package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;

/** Pure state machine for one confirmed Android Auto projection generation. */
final class AutoDisconnectPolicy {
	enum Action {
		NONE,
		SCHEDULE_SHUTDOWN,
		CANCEL_SHUTDOWN
	}

	private State state = State.UNSEEN;

	synchronized Action onConnectionType(int type, boolean projectionAccepted) {
		if (type == CONNECTION_TYPE_PROJECTION) {
			if (!projectionAccepted) return Action.NONE;
			Action action = (state == State.DISCONNECT_PENDING) ?
					Action.CANCEL_SHUTDOWN : Action.NONE;
			state = State.ACTIVE;
			return action;
		}

		if (type == CONNECTION_TYPE_NOT_CONNECTED) {
			if (state != State.ACTIVE) return Action.NONE;
			state = State.DISCONNECT_PENDING;
			return Action.SCHEDULE_SHUTDOWN;
		}

		if (state == State.DISCONNECT_PENDING) {
			state = State.UNSEEN;
			return Action.CANCEL_SHUTDOWN;
		}
		return Action.NONE;
	}

	synchronized boolean onDisconnectTimeout(boolean stillNotConnected) {
		if (state != State.DISCONNECT_PENDING) return false;
		if (!stillNotConnected) {
			state = State.UNSEEN;
			return false;
		}
		state = State.QUIESCENT;
		return true;
	}

	synchronized State stateForTests() {
		return state;
	}

	enum State {
		UNSEEN,
		ACTIVE,
		DISCONNECT_PENDING,
		QUIESCENT
	}
}
