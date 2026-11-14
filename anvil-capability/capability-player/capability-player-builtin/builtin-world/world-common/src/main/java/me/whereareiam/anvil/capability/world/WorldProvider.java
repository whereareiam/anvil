package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the dimension a player is in, over the player's typed channel, for the libraries the world
 * family has a worker side for. Composition creates it only for players whose worker installed the world extension.
 */
public final class WorldProvider implements ProtocolPlayerCapabilityProvider<World> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(WorldChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<World> capability() {
		return World.class;
	}

	@Override
	public @NotNull World create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelWorld(context.channel());
	}
}
