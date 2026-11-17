package me.whereareiam.anvil.protocol.java.api.packet;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One packet the server sent, as it arrived on the wire: the connection's state, the packet's ID and its bytes after
 * the ID, decrypted and decompressed, with minecraft-data's name of the packet and, when the Java protocol models the
 * packet, its value. Every packet a player receives arrives as a frame, whether or not the protocol models it, so
 * frames are the whole stream in arrival order.
 *
 * <p>The name is minecraft-data's, such as {@code map_chunk} or {@code update_light}, which stays the same across the
 * releases that carry the packet; it is absent when Anvil ships no packet names for the player's protocol. The value is
 * the packet the protocol delivers to listeners of its class, which a release that says the same thing in another
 * packet reports as the current one, so the value's class need not match the name.</p>
 *
 * <pre>{@code
 * protocol.listenFrames(frame -> {
 *     if (frame.name().filter("update_light"::equals).isPresent()) lightUpdates++;
 *     frame.packet().ifPresent(packet -> received(packet));
 * });
 * }</pre>
 */
@ToString
@EqualsAndHashCode
public final class ClientboundFrame {
	/**
	 * The connection's state when the packet arrived, which decides what its ID means.
	 */
	@Getter
	private final @NotNull ConnectionState state;
	/**
	 * The packet's ID on the wire.
	 */
	@Getter
	private final int id;
	@Getter(AccessLevel.NONE)
	private final @Nullable String name;
	@Getter(AccessLevel.NONE)
	@ToString.Exclude
	private final byte @NotNull [] payload;
	@Getter(AccessLevel.NONE)
	private final @Nullable ClientboundPacket packet;

	/**
	 * Creates a frame.
	 *
	 * @param state the connection's state when the packet arrived
	 * @param id the packet's ID on the wire
	 * @param name minecraft-data's name of the packet, or {@code null} when Anvil has no packet names for the protocol
	 * @param payload the packet's bytes after its ID; the frame keeps a copy
	 * @param packet the packet's value, or {@code null} when the protocol does not model the packet
	 */
	public ClientboundFrame(
			@NotNull ConnectionState state,
			int id,
			@Nullable String name,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
		this.state = state;
		this.id = id;
		this.name = name;
		this.payload = payload.clone();
		this.packet = packet;
	}

	/**
	 * Returns minecraft-data's name of the packet.
	 *
	 * @return the name, such as {@code map_chunk}, or empty when Anvil ships no packet names for the protocol
	 */
	public @NotNull Optional<String> name() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the packet's bytes after its ID, decrypted and decompressed.
	 *
	 * @return a copy of the bytes
	 */
	public byte @NotNull [] payload() {
		return payload.clone();
	}

	/**
	 * Returns how many bytes the packet has after its ID.
	 *
	 * @return the payload's length
	 */
	public int size() {
		return payload.length;
	}

	/**
	 * Returns the packet's value.
	 *
	 * @return the value, or empty when the Java protocol does not model the packet
	 */
	public @NotNull Optional<ClientboundPacket> packet() {
		return Optional.ofNullable(packet);
	}
}
