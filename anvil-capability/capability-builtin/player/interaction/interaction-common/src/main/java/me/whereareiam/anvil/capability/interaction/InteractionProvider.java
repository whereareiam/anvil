package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for item, block and entity interaction over the player's typed channel, for the libraries the
 * interaction family has a worker side for. Composition creates it only for players whose worker installed the
 * interaction binding.
 */
public final class InteractionProvider implements ProtocolPlayerCapabilityProvider<Interaction> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.interaction";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
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
