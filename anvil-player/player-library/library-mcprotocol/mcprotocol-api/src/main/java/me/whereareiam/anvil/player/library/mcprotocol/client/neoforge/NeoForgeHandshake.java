package me.whereareiam.anvil.player.library.mcprotocol.client.neoforge;

import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.type.network.ChannelFlow;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import me.whereareiam.anvil.player.library.mcprotocol.client.ProtocolReader;
import me.whereareiam.anvil.player.library.mcprotocol.client.ProtocolWriter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Answers the custom payloads of NeoForge's configuration as a NeoForge client of Minecraft 1.21.1 does, with the
 * payload channels the player claims. The server asks for the client's channels and refuses the client unless it
 * claims every channel the server requires, at the server's version; then it syncs its frozen registries, its
 * configuration files, its registry data maps, its extensible enums and its feature flags, and waits for the client's
 * acknowledgement of each. The client takes the server's registries, enums and flags as its own, as a client with the
 * same mods would have them, and runs no mod code: every other payload is left unanswered.
 *
 * <p>NeoForge's network writes a connection phase by its ordinal in Minecraft's {@code ConnectionProtocol} and a
 * direction by its ordinal in {@code PacketFlow}, both fixed for Minecraft 1.21.1.</p>
 */
public final class NeoForgeHandshake {
	/**
	 * The channel on which the server asks for the client's channels and the client claims them.
	 */
	public static final String QUERY = "neoforge:register";
	private static final String REGISTRIES_SYNCED = "neoforge:frozen_registry_sync_completed";
	private static final String FEATURE_FLAGS = "neoforge:feature_flags";
	private static final String FEATURE_FLAGS_ACKNOWLEDGED = "neoforge:feature_flags_ack";
	private static final String ENUMS = "neoforge:extensible_enum_data";
	private static final String ENUMS_ACKNOWLEDGED = "neoforge:extensible_enum_ack";
	private static final String DATA_MAPS = "neoforge:known_registry_data_maps";
	private static final String DATA_MAPS_KNOWN = "neoforge:known_registry_data_maps_reply";
	private static final String COMMON_VERSION = "c:version";
	private static final String COMMON_REGISTER = "c:register";
	private static final Map<ChannelPhase, Integer> PROTOCOL_ORDINALS = Map.of(ChannelPhase.PLAY, 1, ChannelPhase.CONFIGURATION, 4);
	private static final Map<ChannelFlow, Integer> FLOW_ORDINALS = Map.of(ChannelFlow.SERVERBOUND, 0, ChannelFlow.CLIENTBOUND, 1);
	private static final byte[] EMPTY = new byte[0];

	private final List<PayloadChannel> channels;
	private final Map<String, Function<byte[], Answer>> answers;

	/**
	 * Creates the handshake of a client that claims the given channels.
	 *
	 * @param channels the payload channels the client claims
	 */
	public NeoForgeHandshake(@NotNull List<PayloadChannel> channels) {
		this.channels = List.copyOf(channels);
		this.answers = Map.of(
				QUERY, query -> new Answer(QUERY, claimed()),
				REGISTRIES_SYNCED, done -> new Answer(REGISTRIES_SYNCED, EMPTY),
				FEATURE_FLAGS, flags -> new Answer(FEATURE_FLAGS_ACKNOWLEDGED, EMPTY),
				ENUMS, enums -> new Answer(ENUMS_ACKNOWLEDGED, EMPTY),
				DATA_MAPS, maps -> new Answer(DATA_MAPS_KNOWN, known(maps)),
				COMMON_VERSION, versions -> new Answer(COMMON_VERSION, versions),
				COMMON_REGISTER, register -> new Answer(COMMON_REGISTER, register)
		);
	}

	/**
	 * Answers a custom payload the server sent during the configuration.
	 *
	 * @param channel the payload's channel
	 * @param payload the payload's bytes
	 * @return the payload to send back, or empty when a NeoForge client sends none
	 */
	public @NotNull Optional<Answer> answer(@NotNull String channel, byte @NotNull [] payload) {
		Function<byte[], Answer> answer = answers.get(channel);

		return answer == null ? Optional.empty() : Optional.of(answer.apply(payload));
	}

	/**
	 * Writes the client's channels as NeoForge's query reply: for each phase, the phase's ordinal and the channels in
	 * it, each its id, version, optional direction and whether it is optional.
	 */
	private byte[] claimed() {
		ProtocolWriter writer = new ProtocolWriter().varInt(PROTOCOL_ORDINALS.size());
		for (ChannelPhase phase : ChannelPhase.values()) {
			List<PayloadChannel> inPhase = channels.stream().filter(channel -> channel.getPhase() == phase).toList();
			writer.varInt(PROTOCOL_ORDINALS.get(phase)).varInt(inPhase.size());
			inPhase.forEach(channel -> claim(writer, channel));
		}

		return writer.toByteArray();
	}

	private static void claim(ProtocolWriter writer, PayloadChannel channel) {
		writer.string(channel.getId()).string(channel.getVersion()).bool(channel.getFlow() != null);
		if (channel.getFlow() != null) writer.varInt(FLOW_ORDINALS.get(channel.getFlow()));
		writer.bool(channel.isOptional());
	}

	/**
	 * Answers the server's registry data maps with every one of them, as a client with the server's mods knows them:
	 * for each registry, the ids of its data maps without whether each is mandatory.
	 */
	private static byte[] known(byte[] maps) {
		ProtocolReader reader = new ProtocolReader(maps);
		int registries = reader.varInt();
		ProtocolWriter writer = new ProtocolWriter().varInt(registries);
		for (int registry = 0; registry < registries; registry++) {
			writer.string(reader.string());
			int count = reader.varInt();
			writer.varInt(count);
			for (int map = 0; map < count; map++) {
				writer.string(reader.string());
				reader.bool();
			}
		}

		return writer.toByteArray();
	}

	/**
	 * A payload the client sends back: its channel and bytes.
	 *
	 * @param channel the channel the payload goes on
	 * @param payload the payload's bytes
	 */
	public record Answer(@NotNull String channel, byte @NotNull [] payload) {
	}
}
