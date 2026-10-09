package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for absolute movement and view changes over the player's typed channel, for the
 * libraries the movement family has a worker side for. Composition creates it only for players whose worker
 * installed the movement binding.
 */
public final class MovementProvider implements ProtocolPlayerCapabilityProvider<Movement> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.movement";

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
	public @NotNull Class<Movement> capability() {
		return Movement.class;
	}

	@Override
	public @NotNull Movement create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel channel = context.channel();
		return position -> channel.request(MovementOperations.MOVE, position);
	}
}
