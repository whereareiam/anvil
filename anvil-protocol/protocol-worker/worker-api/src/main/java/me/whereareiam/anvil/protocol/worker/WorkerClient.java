package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * The native client of one loaded library release, as the native worker drives it. A library creates it in
 * {@link WorkerLibraryProvider#client(Object)}, usually by adapting the client port its client segment implements;
 * the worker's players open, connect and disconnect native sessions only through it.
 *
 * <p>Sessions are opaque to the worker and are passed back to the client that opened them. The client owns every
 * packet it must answer on its own and reports the resulting state through the {@link SessionListener} of the
 * session.</p>
 */
public interface WorkerClient {
	/**
	 * Returns the wire-protocol number of the loaded release's key version, read from the release at run time. The
	 * worker refuses to start unless it equals the number the release data maps the key version to.
	 *
	 * @return native protocol number of the release key
	 */
	int protocolNumber();

	/**
	 * Returns the class of the sessions this client opens; capability bindings receive sessions of this type.
	 *
	 * @return native session class
	 */
	@NotNull Class<?> sessionType();

	/**
	 * Creates an unconnected session for one player. The listener is installed before the session connects and
	 * receives the session it was created for, so that the worker can ignore callbacks of replaced sessions.
	 *
	 * @param options profile, optional credentials and connection of the player
	 * @param listener receiver of login, view and disconnect callbacks for this session
	 * @return new unconnected session
	 * @throws IllegalArgumentException when the client cannot honour an option, such as a connection setting its
	 * protocol does not have
	 */
	@NotNull Object open(@NotNull WorkerPlayerOptions options, @NotNull SessionListener listener);

	/**
	 * Starts connecting a session returned by {@link #open(WorkerPlayerOptions, SessionListener)} without blocking
	 * until login completes.
	 *
	 * @param session session to connect
	 */
	void connect(@NotNull Object session);

	/**
	 * Tests whether a session currently has an open connection.
	 *
	 * @param session session to test
	 * @return whether the session is connected
	 */
	boolean connected(@NotNull Object session);

	/**
	 * Closes a session's connection; the listener later receives the disconnect.
	 *
	 * @param session session to disconnect
	 * @param reason plain-text reason sent to the local session
	 */
	void disconnect(@NotNull Object session, @NotNull String reason);

	/**
	 * Callbacks of one session, each naming the session it reports for.
	 */
	interface SessionListener {
		/**
		 * Reports that the session logged in.
		 *
		 * @param session session that logged in
		 * @param granted profile the server granted, which a proxy or login plugin may have renamed
		 */
		void loggedIn(@NotNull Object session, @NotNull PlayerProfile granted);

		/**
		 * Reports that the server set the player's view, such as when it teleported the player.
		 *
		 * @param session session the server addressed
		 * @param yaw view yaw in degrees
		 * @param pitch view pitch in degrees
		 */
		void teleported(@NotNull Object session, float yaw, float pitch);

		/**
		 * Reports that the session's connection ended.
		 *
		 * @param session session that disconnected
		 * @param cause why the connection ended
		 * @param reason plain-text reason, such as the server's kick message
		 */
		void disconnected(@NotNull Object session, @NotNull DisconnectCause cause, @NotNull String reason);
	}
}
