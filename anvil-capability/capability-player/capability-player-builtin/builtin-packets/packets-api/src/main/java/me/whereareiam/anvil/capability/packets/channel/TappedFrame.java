package me.whereareiam.anvil.capability.packets.channel;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One frame the player received, which the worker sends once for every open tap that takes it.
 */
@Value
public class TappedFrame {
	/**
	 * The IDs of the taps that take the frame.
	 */
	@NotNull List<Integer> taps;
	/**
	 * The connection's state when the packet arrived.
	 */
	@NotNull ConnectionState state;
	/**
	 * The packet's ID on the wire.
	 */
	int id;
	/**
	 * Minecraft-data's name of the packet, or {@code null} when Anvil ships no packet names for the protocol.
	 */
	@Nullable String name;
	/**
	 * The packet's bytes after its ID, or {@code null} when no tap that takes the frame needs them.
	 */
	byte @Nullable [] payload;
	/**
	 * The packet's value, or {@code null} when the protocol does not model the packet.
	 */
	@Nullable ClientboundPacket packet;
}
