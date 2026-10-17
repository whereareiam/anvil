package me.whereareiam.anvil.protocol.java.api;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * The Java Edition protocol of one simulated player, as its worker extensions see it: the packets the player receives
 * and the packets it sends, as immutable values that do not change between releases. The player's library translates
 * them to and from its own packets for the loaded release.
 *
 * <p>The protocol belongs to the player, not to one connection. A listener receives the packets of whichever
 * connection is current until its subscription closes; packets a replaced connection still delivers never reach it.
 * Listeners run on the connection's packet thread, in the order the packets arrive. Bindings can also observe the
 * packets the player sends through the protocol, such as the movement another capability sends.</p>
 *
 * <pre>{@code
 * JavaProtocol protocol = player.protocol(JavaProtocol.class);
 * Subscription chat = protocol.listen(ClientboundSystemChat.class, packet -> received(packet.getText()));
 * protocol.send(new ServerboundChatCommand("spawn"));
 * }</pre>
 */
public interface JavaProtocol {
	/**
	 * Observes the packets of one type the player receives. Packets are final value classes, so the listener receives
	 * exactly the packets of that class.
	 *
	 * @param type packet class
	 * @param listener observer of each packet of that class
	 * @param <P> packet type
	 * @return registration that stops the observation when closed
	 * @throws ProtocolUnavailableException when the loaded release cannot carry the packet
	 */
	<P extends ClientboundPacket> @NotNull Subscription listen(@NotNull Class<P> type, @NotNull Consumer<? super P> listener);

	/**
	 * Sends a packet on the player's current connection.
	 *
	 * @param packet packet to send
	 * @throws ProtocolUnavailableException when the loaded release cannot carry the packet
	 * @throws IllegalStateException when the player is not connected
	 */
	void send(@NotNull ServerboundPacket packet);

	/**
	 * Observes the packets of one type the player sends through this protocol, whichever binding sends them, once
	 * each was sent on the current connection. Packets the player's library sends on its own, such as the acceptance
	 * of a teleport, are not reported. Listeners run on the sender's thread.
	 *
	 * <pre>{@code
	 * Subscription moves = protocol.listenSent(ServerboundMovePlayerPosRot.class, move -> position = move.getPosition());
	 * }</pre>
	 *
	 * @param type packet class
	 * @param listener observer of each packet of that class
	 * @param <P> packet type
	 * @return registration that stops the observation when closed
	 * @throws ProtocolUnavailableException when the loaded release cannot carry the packet
	 */
	<P extends ServerboundPacket> @NotNull Subscription listenSent(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	);

	/**
	 * Tells whether the loaded release carries a packet, so that a binding can choose between alternatives before it
	 * listens or sends.
	 *
	 * @param type packet class
	 * @return whether {@link #listen(Class, Consumer)}, {@link #send(ServerboundPacket)} or
	 * {@link #listenSent(Class, Consumer)} accepts it
	 */
	boolean supports(@NotNull Class<? extends JavaPacket> type);
}
