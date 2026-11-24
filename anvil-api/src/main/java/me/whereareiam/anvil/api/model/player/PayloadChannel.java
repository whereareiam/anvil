package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.network.ChannelFlow;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A payload channel a mod registers with NeoForge's network: the payloads of one kind, in one phase of the connection.
 * A NeoForge client and server claim their channels to each other while the configuration starts, and the server
 * refuses a client that lacks a channel it requires or claims one at another version.
 *
 * <pre>{@code
 * PayloadChannel channel = PayloadChannel.builder()
 *         .id("create:place_schematic")
 *         .version("6.0.10")
 *         .phase(ChannelPhase.PLAY)
 *         .flow(ChannelFlow.SERVERBOUND)
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class PayloadChannel {
	/**
	 * The channel's id, a resource location such as {@code create:place_schematic}.
	 */
	@NotNull String id;

	/**
	 * The version the mod registered the channel at; both sides must claim the same one.
	 */
	@NotNull String version;

	/**
	 * The phase the channel carries payloads in.
	 */
	@NotNull ChannelPhase phase;

	/**
	 * The one direction the channel carries payloads in, or null for both.
	 */
	@Nullable ChannelFlow flow;

	/**
	 * Whether the side that registered the channel accepts a peer without it.
	 */
	boolean optional;
}
