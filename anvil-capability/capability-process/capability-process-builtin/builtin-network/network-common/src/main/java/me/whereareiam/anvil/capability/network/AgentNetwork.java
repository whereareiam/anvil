package me.whereareiam.anvil.capability.network;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.type.network.ChannelFlow;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.network.agent.NetworkOperations;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannel;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannelList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * The network capability of one NeoForge server, read through its agent channel.
 */
@RequiredArgsConstructor
final class AgentNetwork implements Network {
	private static final ChannelOperation<Void, AgentChannelList> CHANNELS = new ChannelOperation<>(
			NetworkOperations.CHANNELS.getName(),
			NetworkOperations.CHANNELS.getRequestType(),
			NetworkOperations.CHANNELS.getResponseType()
	);

	private final @NotNull RequestChannel channel;

	@Override
	public @NotNull List<PayloadChannel> channels() {
		return Objects.requireNonNull(channel.request(CHANNELS, null)).getChannels().stream()
				.map(AgentNetwork::channel)
				.toList();
	}

	private static PayloadChannel channel(AgentChannel channel) {
		return PayloadChannel.builder()
				.id(channel.getId())
				.version(channel.getVersion())
				.phase(ChannelPhase.valueOf(channel.getPhase().name()))
				.flow(channel.getFlow() == null ? null : ChannelFlow.valueOf(channel.getFlow().name()))
				.optional(channel.isOptional())
				.build();
	}
}
