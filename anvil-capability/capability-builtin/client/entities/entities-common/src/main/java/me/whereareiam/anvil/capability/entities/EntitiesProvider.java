package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the entities a player sees, over the player's typed channel, for the libraries the entities
 * family has a worker side for. Composition creates it only for players whose worker installed the entities binding.
 */
public final class EntitiesProvider implements ProtocolPlayerCapabilityProvider<Entities> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.entities";

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
	public @NotNull Class<Entities> capability() {
		return Entities.class;
	}

	@Override
	public @NotNull Entities create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelEntities(context.channel());
	}
}
