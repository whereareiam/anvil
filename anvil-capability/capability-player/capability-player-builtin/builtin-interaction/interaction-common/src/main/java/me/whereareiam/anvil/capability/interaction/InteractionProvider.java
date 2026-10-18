package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for item, block and entity interaction over the player's typed channel, for the libraries the
 * interaction family has a worker side for. Composition creates it only for players whose worker installed the
 * interaction binding.
 */
public final class InteractionProvider implements ProtocolPlayerCapabilityProvider<Interaction> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(InteractionChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Interaction> capability() {
		return Interaction.class;
	}

	@Override
	public @NotNull Interaction create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelInteraction(context.channel());
	}
}
