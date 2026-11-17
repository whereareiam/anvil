package me.whereareiam.anvil.protocol.java.packet;

import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.model.PacketDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The names of the packets a server sends in one protocol, which every Minecraft version speaking it shares, looked
 * up by connection state and packet ID as they arrive on the wire.
 *
 * <p>The names are minecraft-data's, such as {@code map_chunk} for a chunk with its light and {@code block_change}
 * for one block's update, not the names of Mojang's code; minecraft-data keeps one name for a packet across the
 * releases that carry it. {@link JavaPacketData} creates registries; they are immutable and safe to share between
 * threads.</p>
 *
 * <pre>{@code
 * PacketRegistry registry = JavaPacketData.shipped().require(774);
 * String name = registry.clientbound(ConnectionState.PLAY, 0x2c).orElse("unknown");
 * }</pre>
 */
public final class PacketRegistry {
	private final int protocol;
	private final @NotNull PacketDataSource source;
	private final @NotNull Map<ConnectionState, List<String>> clientbound;

	/**
	 * Creates a registry.
	 *
	 * @param protocol the protocol number
	 * @param source where the names come from
	 * @param clientbound the names of the packets a server sends in each state, by packet ID; a state the protocol
	 * lacks has none
	 * @throws IllegalArgumentException when a name is blank or listed twice in one state
	 */
	PacketRegistry(
			int protocol,
			@NotNull PacketDataSource source,
			@NotNull Map<ConnectionState, List<String>> clientbound
	) {
		Map<ConnectionState, List<String>> names = new EnumMap<>(ConnectionState.class);
		for (ConnectionState state : ConnectionState.values()) {
			List<String> listed = List.copyOf(clientbound.getOrDefault(state, List.of()));
			if (listed.stream().anyMatch(String::isBlank))
				throw new IllegalArgumentException("Protocol " + protocol + " names a " + state + " packet blankly");
			if (listed.stream().distinct().count() != listed.size())
				throw new IllegalArgumentException("Protocol " + protocol + " names two " + state + " packets alike");
			names.put(state, listed);
		}

		this.protocol = protocol;
		this.source = source;
		this.clientbound = Collections.unmodifiableMap(names);
	}

	/**
	 * Returns the protocol number the names belong to.
	 *
	 * @return the protocol, such as {@code 774}
	 */
	public int protocol() {
		return protocol;
	}

	/**
	 * Returns where the names come from.
	 *
	 * @return the data's source
	 */
	public @NotNull PacketDataSource source() {
		return source;
	}

	/**
	 * Names a packet the server sends.
	 *
	 * @param state the connection's state when the packet arrived
	 * @param id the packet's ID on the wire
	 * @return minecraft-data's name of the packet, or empty when the protocol has no such packet in that state
	 */
	public @NotNull Optional<String> clientbound(@NotNull ConnectionState state, int id) {
		List<String> names = clientbound.get(state);
		if (id < 0 || id >= names.size()) return Optional.empty();

		return Optional.of(names.get(id));
	}

	/**
	 * Finds the ID of a packet the server sends, by its name.
	 *
	 * @param state the connection state the packet is sent in
	 * @param name minecraft-data's name of the packet, such as {@code map_chunk}
	 * @return the packet's ID, or empty when the protocol has no packet of that name in that state
	 */
	public @NotNull OptionalInt clientboundId(@NotNull ConnectionState state, @NotNull String name) {
		int id = clientbound.get(state).indexOf(name);
		return id < 0 ? OptionalInt.empty() : OptionalInt.of(id);
	}

	/**
	 * Lists the packets the server sends in a state.
	 *
	 * @param state the connection state
	 * @return minecraft-data's names by packet ID, empty when the protocol has no such state
	 */
	public @NotNull List<String> clientbound(@NotNull ConnectionState state) {
		return clientbound.get(state);
	}
}
