package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for absolute movement and view changes over the player's typed channel, for the
 * libraries the movement family has a worker side for. Composition creates it only for players whose worker
 * installed the movement binding.
 */
public final class MovementProvider implements ProtocolPlayerCapabilityProvider<Movement> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(MovementChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Movement> capability() {
		return Movement.class;
	}

	@Override
	public @NotNull Movement create(@NotNull ProtocolPlayerCapabilityContext context) {
		ProtocolChannel channel = context.channel();
		return position -> channel.request(MovementChannel.MOVE, position);
	}
}
