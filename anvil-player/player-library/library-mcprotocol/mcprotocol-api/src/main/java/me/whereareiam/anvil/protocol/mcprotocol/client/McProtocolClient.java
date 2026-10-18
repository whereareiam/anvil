package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.jetbrains.annotations.NotNull;

/**
 * Client side of one MCProtocolLib release, implemented by exactly one client segment on a worker's class path.
 *
 * <p>The worker shell never links against MCProtocolLib: it opens, connects and disconnects native sessions and
 * exchanges Java Edition packets only through this port, and the segment translates them to and from the release's
 * own packets. A segment owns every packet the client must answer on its own, such as sending
 * client information after login, accepting teleports and announcing that the player loaded, and reports the
 * resulting state through {@link ClientListener}. Sessions are opaque to the shell and are passed back to the
 * segment that created them.</p>
 *
 * <p>Segments register their implementation in
 * {@code META-INF/services/me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient}: an adapter per segment,
 * which lists its release's translations in a {@link PacketTable} and hands them to the client base its side
 * shares:</p>
 *
 * <pre>{@code
 * public final class McProtocolClientAdapter extends JavaClient<ClientSession> {
 *     public McProtocolClientAdapter() {
 *         super(new NetworkClientSessions(TeleportListener::new), McProtocolClientAdapter::packets);
 *     }
 *
 *     static PacketTable<Packet> packets() {
 *         return new PacketTable<>(List.of(new ChatPackets(), new InteractionPackets()));
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the release
 */
public interface McProtocolClient<S> {
	/**
	 * Identifier of the MCProtocolLib library: scenario and player declarations select it, stored accounts and
	 * segment descriptors name it, and its release cache lies below it. The library's host side and the worker
	 * shell both read it from this port, so neither depends on the other.
	 */
	String LIBRARY_ID = "mcprotocol";

	/**
	 * Returns the wire-protocol number of the loaded release, read from the release at run time.
	 *
	 * @return native protocol number
	 */
	int protocolNumber();

	/**
	 * Returns the class of the sessions this client opens; capability bindings receive sessions of this type.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Creates an unconnected session for one login. The listener is installed before the session connects
	 * and receives the session it was created for, so that callers can ignore callbacks of replaced sessions.
	 *
	 * <p>The session announces the connection's virtual host in its handshake when one is set, and binds its
	 * socket to the connection's source address when one is set; a segment never ignores either value.</p>
	 *
	 * @param login profile, optional credentials, client settings and connection
	 * @param listener receiver of login, teleport and disconnect callbacks for this session
	 * @return new unconnected session
	 */
	@NotNull S open(@NotNull ClientLogin login, @NotNull ClientListener<? super S> listener);

	/**
	 * Starts connecting a session returned by {@link #open(ClientLogin, ClientListener)} without blocking
	 * until login completes.
	 *
	 * @param session session to connect
	 */
	void connect(@NotNull S session);

	/**
	 * Tests whether a session currently has an open connection.
	 *
	 * @param session session to test
	 * @return whether the session is connected
	 */
	boolean connected(@NotNull S session);

	/**
	 * Closes a session's connection; the listener later receives the disconnect.
	 *
	 * @param session session to disconnect
	 * @param reason plain-text reason sent to the local session
	 */
	void disconnect(@NotNull S session, @NotNull String reason);

	/**
	 * Sends a Java Edition packet on a session, translated to the release's own packet.
	 *
	 * @param session connected session
	 * @param packet packet to send
	 * @throws ProtocolUnavailableException when the release cannot carry the packet
	 */
	void send(@NotNull S session, @NotNull ServerboundPacket packet);

	/**
	 * Tells whether the release carries a Java Edition packet: whether the segment translates it from the release's
	 * packets for {@link ClientListener#received(Object, ClientboundPacket)}, or to them for
	 * {@link #send(Object, ServerboundPacket)}.
	 *
	 * @param type packet class
	 * @return whether the release carries it
	 */
	boolean supports(@NotNull Class<? extends JavaPacket> type);
}
