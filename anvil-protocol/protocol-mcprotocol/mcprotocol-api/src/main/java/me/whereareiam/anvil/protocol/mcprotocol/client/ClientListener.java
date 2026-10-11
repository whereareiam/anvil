package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
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
	 * @param granted profile the server or proxy granted in its login success, which a proxy or login plugin
	 * may have changed from the profile the client asked for
	 */
	void loggedIn(@NotNull S session, @NotNull ClientProfile granted);

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
	 * Reports that a session's connection ended. A session can report more than one disconnect, in either
	 * order: the server's disconnect packet as {@link DisconnectCause#SERVER}, a refused encryption request as
	 * {@link DisconnectCause#AUTHENTICATION_REQUIRED}, and the closed connection as
	 * {@link DisconnectCause#CONNECTION_LOST}. A client never reports {@link DisconnectCause#CLIENT}; its caller
	 * knows when it closed the session itself.
	 *
	 * @param session session that disconnected
	 * @param cause what ended the connection, as far as this report knows
	 * @param reason the reason flattened to plain text, including translation keys and their arguments
	 */
	void disconnected(@NotNull S session, @NotNull DisconnectCause cause, @NotNull String reason);
}
