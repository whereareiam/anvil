package me.whereareiam.anvil.protocol.mcprotocol.client;

import org.jetbrains.annotations.NotNull;

/**
 * Receives the connection state of sessions opened by a {@link McProtocolClient}.
 *
 * <p>Callbacks arrive on the release's network threads. A login is reported after the client sent its client
 * information, and a teleport before the client accepts it. Each callback names the session it belongs to; a
 * session that was replaced by a reconnect can still deliver callbacks while it closes.</p>
 *
 * @param <S> native session type of the release
 */
public interface ClientListener<S> {
	/**
	 * Reports that the server accepted the login and the client sent its client information.
	 *
	 * @param session session that logged in
	 */
	void loggedIn(@NotNull S session);

	/**
	 * Reports a server-set position and view before the client accepts the teleport, so that whoever observes the
	 * server completing the teleport already reads the new view.
	 *
	 * @param session session that was teleported
	 * @param yaw view yaw in degrees, as sent by the server
	 * @param pitch view pitch in degrees, as sent by the server
	 */
	void teleported(@NotNull S session, float yaw, float pitch);

	/**
	 * Reports that a session's connection closed, for any reason. A session can report more than one
	 * disconnect, for example a disconnect packet followed by the closed connection.
	 *
	 * @param session session that disconnected
	 * @param reason the reason flattened to plain text, including translation keys and their arguments
	 */
	void disconnected(@NotNull S session, @NotNull String reason);
}
