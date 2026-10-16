package me.whereareiam.anvil.protocol.cloudburst.client;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import org.jetbrains.annotations.NotNull;

/**
 * Receives the connection state of sessions opened by a {@link CloudburstClient}.
 *
 * <p>Callbacks arrive on the release's network threads. A login is reported once the client announced that its
 * player is initialized, and a server-set view before the client acts on it. Each callback names the session it
 * belongs to; a session that was replaced by a reconnect can still deliver callbacks while it closes.</p>
 *
 * @param <S> native session type of the release
 */
public interface ClientListener<S> {
	/**
	 * Reports that the server spawned the player and the client announced that its player is initialized.
	 *
	 * @param session session that logged in
	 * @param granted profile the server lists for the player, which a server may have renamed or given another
	 * identity than the one the login claimed
	 */
	void loggedIn(@NotNull S session, @NotNull ClientProfile granted);

	/**
	 * Reports a server-set view of the player, such as the spawn rotation or a teleport.
	 *
	 * @param session session that was turned
	 * @param yaw view yaw in degrees, as sent by the server
	 * @param pitch view pitch in degrees, as sent by the server
	 */
	void teleported(@NotNull S session, float yaw, float pitch);

	/**
	 * Reports that a session's connection ended. A session can report more than one disconnect, in either order:
	 * the server's disconnect packet or refused login as {@link DisconnectCause#SERVER} or
	 * {@link DisconnectCause#AUTHENTICATION_REQUIRED}, and the closed connection as
	 * {@link DisconnectCause#CONNECTION_LOST}. A client never reports {@link DisconnectCause#CLIENT}; its caller
	 * knows when it closed the session itself.
	 *
	 * @param session session that disconnected
	 * @param cause what ended the connection, as far as this report knows
	 * @param reason the reason as plain text, such as the server's kick message or its translation key
	 */
	void disconnected(@NotNull S session, @NotNull DisconnectCause cause, @NotNull String reason);
}
