package me.aap.fermata.addon;

/** Owns addon resources that must not survive a confirmed Android Auto disconnect. */
public interface AutomotiveShutdownParticipant extends FermataAddon {
	/** Releases threads, sockets, servers and playback state without deleting user data. */
	void onAutomotiveShutdown();

	/** Re-enables eager runtime facilities for a later confirmed projection generation. */
	default void onAutomotiveSessionStarted() {
	}
}
