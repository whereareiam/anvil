package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.TappedPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * The packets a selection names that the player received since the tap opened, in the order they arrived, which
 * {@link Packets#tap(PacketSelection)} creates. The tap reads them through a cursor: each wait looks at the packets
 * after the cursor, oldest first, and moves the cursor past the one it returns, so two waits for the same packet class
 * see two packets and a later wait never sees an earlier packet again. Taps carry values, not bytes; a recording keeps
 * the bytes.
 *
 * <p>The tap keeps its packets until it closes; closing stops the player's worker sending them.</p>
 *
 * <pre>{@code
 * PacketSelection repair = PacketSelection.of(ClientboundLevelChunkWithLight.class, ClientboundBlockUpdate.class);
 * try (PacketTap tap = packets.tap(repair)) {
 *     console.run("ptest reload-chunk");
 *     tap.received(ClientboundLevelChunkWithLight.class, chunk -> chunk.position().equals(position));
 *     tap.received(ClientboundBlockUpdate.class, update -> update.change().position().equals(fake));
 * }
 * }</pre>
 */
public interface PacketTap extends AutoCloseable {
	/**
	 * Returns which packets the tap takes.
	 *
	 * @return the selection
	 */
	@NotNull PacketSelection selection();

	/**
	 * Waits for a packet of a class the condition accepts, within {@link SimulatedPlayer#DEFAULT_TIMEOUT}.
	 *
	 * @param type the packet class, which the selection takes
	 * @param condition what the packet must be
	 * @param <P> packet type
	 * @return the packet
	 * @throws IllegalStateException when no such packet arrives in time
	 * @throws IllegalArgumentException when the selection does not take the class
	 */
	default <P extends ClientboundPacket> @NotNull P received(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition
	) {
		return received(type, condition, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a packet of a class the condition accepts.
	 *
	 * @param type the packet class, which the selection takes
	 * @param condition what the packet must be
	 * @param timeout how long to wait
	 * @param <P> packet type
	 * @return the packet
	 * @throws IllegalStateException when no such packet arrives in time
	 * @throws IllegalArgumentException when the selection does not take the class
	 */
	<P extends ClientboundPacket> @NotNull P received(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition,
			@NotNull Duration timeout
	);

	/**
	 * Waits for a packet of a minecraft-data name, which the protocol need not model, within
	 * {@link SimulatedPlayer#DEFAULT_TIMEOUT}.
	 *
	 * @param name the packet's name, such as {@code update_light}
	 * @return the packet
	 * @throws IllegalStateException when no such packet arrives in time
	 */
	default @NotNull TappedPacket received(@NotNull String name) {
		return received(name, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for a packet of a minecraft-data name, which the protocol need not model.
	 *
	 * @param name the packet's name, such as {@code update_light}
	 * @param timeout how long to wait
	 * @return the packet
	 * @throws IllegalStateException when no such packet arrives in time
	 */
	@NotNull TappedPacket received(@NotNull String name, @NotNull Duration timeout);

	/**
	 * Checks that no packet of a class the condition accepts arrives for a while.
	 *
	 * @param type the packet class, which the selection takes
	 * @param condition the packets that must not arrive
	 * @param duration how long to check
	 * @param <P> packet type
	 * @throws IllegalStateException when such a packet arrives
	 * @throws IllegalArgumentException when the selection does not take the class
	 */
	<P extends ClientboundPacket> void neverReceived(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition,
			@NotNull Duration duration
	);

	/**
	 * Creates an expectation, met once the tap has an untaken packet of a class the condition accepts, for a player to
	 * wait on together with others.
	 *
	 * @param type the packet class, which the selection takes
	 * @param condition what the packet must be
	 * @param <P> packet type
	 * @return the expectation
	 * @throws IllegalArgumentException when the selection does not take the class
	 */
	<P extends ClientboundPacket> @NotNull Expectation expectReceived(
			@NotNull Class<P> type,
			@NotNull Predicate<? super P> condition
	);

	/**
	 * Returns every packet the tap received, in the order they arrived.
	 *
	 * @return the packets
	 */
	@NotNull List<TappedPacket> history();

	/**
	 * Returns the packets after the cursor, without waiting or moving it.
	 *
	 * @return the packets no wait has passed yet, oldest first
	 */
	@NotNull List<TappedPacket> unread();

	/**
	 * Stops taking packets and forgets them.
	 */
	@Override
	void close();
}
