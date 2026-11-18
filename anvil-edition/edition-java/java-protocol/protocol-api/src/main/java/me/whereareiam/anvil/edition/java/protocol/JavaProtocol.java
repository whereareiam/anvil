package me.whereareiam.anvil.edition.java.protocol;

import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
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
	 * Observes every packet the player receives as its {@link ClientboundFrame}, in the order the packets arrive on the
	 * wire: those the protocol models with their value, and every other one with its bytes alone. While a frame
	 * listener exists, the release decodes every packet it models except the {@linkplain ClientboundPackets#isCostly
	 * costly} ones, such as a chunk column, whose frames carry their value only while something wants their class: a
	 * listener of the class, {@link #want(Class)}, or the player's {@link #world()} once something asked for it.
	 *
	 * <pre>{@code
	 * Subscription frames = protocol.listenFrames(frame -> recorded.add(frame));
	 * }</pre>
	 *
	 * @param listener observer of each frame
	 * @return registration that stops the observation when closed
	 * @throws ProtocolUnavailableException when the player's library does not hand over the frames it receives
	 */
	@NotNull Subscription listenFrames(@NotNull Consumer<? super ClientboundFrame> listener);

	/**
	 * Wants the packets of a class while the subscription is open, so the release decodes them and the frames of a
	 * {@linkplain ClientboundPackets#isCostly costly} packet carry its value, without listening to them by class.
	 *
	 * <pre>{@code
	 * Subscription chunks = protocol.want(ClientboundLevelChunkWithLight.class);
	 * Subscription frames = protocol.listenFrames(frame -> frame.packet().ifPresent(this::matched));
	 * }</pre>
	 *
	 * @param type packet class
	 * @return registration that stops wanting the class when closed
	 * @throws ProtocolUnavailableException when the loaded release cannot carry the packet
	 */
	@NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type);

	/**
	 * Tells whether the player's library hands over the frames it receives, so that a binding that needs them can
	 * report itself unavailable before it listens.
	 *
	 * @return whether {@link #listenFrames(Consumer)} accepts listeners
	 */
	boolean handsOverFrames();

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

	/**
	 * Returns the number of the protocol the loaded release speaks, such as 774 for Minecraft 1.21.11, which every
	 * Minecraft version of that protocol shares. It names what the release's numbers mean, such as the global block
	 * state IDs of its chunks.
	 *
	 * <pre>{@code
	 * BlockRegistry blocks = JavaBlockData.shipped().require(protocol.protocolNumber());
	 * }</pre>
	 *
	 * @return the protocol number
	 */
	int protocolNumber();

	/**
	 * Returns the world the player's client knows, which the packets this protocol receives keep up to date. The first
	 * call makes the player keep chunks; until then the release does not decode them. Every call returns the same
	 * world, which belongs to the player, not to one connection.
	 *
	 * <pre>{@code
	 * JavaWorld world = protocol.world();
	 * OptionalInt below = world.blockState(x, y - 1, z);
	 * }</pre>
	 *
	 * @return the player's world
	 */
	@NotNull JavaWorld world();

	/**
	 * Requires the packets a feature needs, so that a binding whose feature the loaded release lacks, such as cookies
	 * before Minecraft 1.20.5, fails before it registers anything.
	 *
	 * <pre>{@code
	 * protocol.require("cookies", List.of(ClientboundCookieRequest.class, ServerboundCookieResponse.class));
	 * }</pre>
	 *
	 * @param feature what the packets carry, which the failure names, such as {@code cookies}
	 * @param packets packet classes the feature needs
	 * @throws ProtocolUnavailableException naming the first packet the loaded release does not carry; the worker
	 * reports the binding's capability as unavailable with its message
	 */
	default void require(@NotNull String feature, @NotNull Collection<Class<? extends JavaPacket>> packets) {
		for (Class<? extends JavaPacket> packet : packets) {
			if (!supports(packet))
				throw new ProtocolUnavailableException("the player's release has no " + feature + "; it does not carry "
						+ packet.getName());
		}
	}
}
