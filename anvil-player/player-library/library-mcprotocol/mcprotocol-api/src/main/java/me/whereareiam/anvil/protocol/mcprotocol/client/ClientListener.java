package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Receives the connection state and the translated packets of sessions opened by a {@link McProtocolClient}.
 *
 * <p>Callbacks arrive on the release's network threads. A login is reported after the client sent its client
 * information, and a teleport before the client accepts it. Each callback names the session it belongs to; a
 * session that was replaced by a reconnect can still deliver callbacks while it closes.</p>
 *
 * <p>The listener is also the demand of the session's translations: a packet that is costly to decode, such as a chunk
 * column, is decoded only while the listener {@linkplain #wants(Class) wants} it. While it {@linkplain #wantsFrames()
 * wants frames}, the session hands it every packet it receives as its frame instead, in the order the packets arrive on
 * the wire.</p>
 *
 * @param <S> native session type of the release
 */
public interface ClientListener<S> extends PacketDemand {
	/**
	 * Reports the profile the server or proxy granted in its login success, which arrives before the game starts. A
	 * proxy can keep the client on the loading screen for a while before it starts the game, or never start it.
	 *
	 * @param session session whose login succeeded
	 * @param granted profile the server or proxy granted, which a proxy or login plugin may have changed from the
	 * profile the client asked for
	 */
	default void granted(@NotNull S session, @NotNull ClientProfile granted) {
	}

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

	/**
	 * Delivers a packet the session received, translated to the Java Edition protocol, while the listener wants no
	 * frames. A segment translates every packet it can carry, in the order they arrive, whether or not anyone listens.
	 *
	 * @param session session that received the packet
	 * @param packet the received packet
	 */
	void received(@NotNull S session, @NotNull ClientboundPacket packet);

	/**
	 * Tells whether the listener wants each packet the session receives as its frame, which the session then copies
	 * and delivers through {@link #received(Object, ConnectionState, int, byte[], ClientboundPacket)} instead.
	 *
	 * @return whether frames are wanted
	 */
	boolean wantsFrames();

	/**
	 * Delivers a packet the session received as its frame, while the listener wants frames: every packet, in the order
	 * they arrive on the wire, with its translation when the segment translates it.
	 *
	 * @param session session that received the packet
	 * @param state the connection's state when the packet arrived
	 * @param id the packet's ID on the wire
	 * @param payload the packet's bytes after its ID, decrypted and decompressed
	 * @param packet the packet translated to the Java Edition protocol, or {@code null} when the segment does not
	 * translate it
	 */
	void received(
			@NotNull S session,
			@NotNull ConnectionState state,
			int id,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	);
}
