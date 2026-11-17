package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider of the packets a player receives, over the player's typed channel, for the libraries the packets
 * family has a worker side for. Composition creates it only for players whose worker installed the packets extension,
 * which a library that hands over no frames does not.
 */
public final class PacketsProvider implements ProtocolPlayerCapabilityProvider<Packets> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(PacketsChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Packets> capability() {
		return Packets.class;
	}

	@Override
	public @NotNull Packets create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelPackets(context.channel(), context.playerName(), context.clientVersion().toString());
	}
}
