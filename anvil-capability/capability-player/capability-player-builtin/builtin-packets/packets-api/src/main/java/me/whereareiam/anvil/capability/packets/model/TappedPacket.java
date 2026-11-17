package me.whereareiam.anvil.capability.packets.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One packet a tap received, without its bytes: where it stands among the packets the tap received, the connection's
 * state, the packet's ID and minecraft-data's name of it, and its value when the Java protocol models the packet.
 */
@Value
public class TappedPacket {
	/**
	 * The packet's position among the packets the tap received, from {@code 0}, in the order they arrived.
	 */
	long sequence;
	/**
	 * The connection's state when the packet arrived.
	 */
	@NotNull ConnectionState state;
	/**
	 * The packet's ID on the wire.
	 */
	int id;
	@Getter(AccessLevel.NONE)
	@Nullable String name;
	@Getter(AccessLevel.NONE)
	@Nullable ClientboundPacket packet;

	/**
	 * Returns minecraft-data's name of the packet.
	 *
	 * @return the name, such as {@code map_chunk}, or empty when Anvil ships no packet names for the protocol
	 */
	public @NotNull Optional<String> name() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the packet's value.
	 *
	 * @return the value, or empty when the Java protocol does not model the packet
	 */
	public @NotNull Optional<ClientboundPacket> packet() {
		return Optional.ofNullable(packet);
	}

	/**
	 * Returns the packet's value when it is of a class.
	 *
	 * @param type the packet class
	 * @param <P> packet type
	 * @return the value, or empty when the packet has no value of that class
	 */
	public <P extends ClientboundPacket> @NotNull Optional<P> packet(@NotNull Class<P> type) {
		return type.isInstance(packet) ? Optional.of(type.cast(packet)) : Optional.empty();
	}
}
