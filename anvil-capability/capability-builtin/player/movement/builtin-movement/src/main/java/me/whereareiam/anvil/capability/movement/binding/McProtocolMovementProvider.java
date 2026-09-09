package me.whereareiam.anvil.capability.movement.binding;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.MovementOperations;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for absolute movement and view changes.
 */
public final class McProtocolMovementProvider implements ProtocolPlayerCapabilityProvider<Movement> {
	static final String ID = "me.whereareiam.anvil.movement";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Movement> capability() {
		return Movement.class;
	}

	@Override
	public @NotNull Movement create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel connection = context.channel();
		if (!connection.installedCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return position -> connection.request(MovementOperations.MOVE, position);
	}
}
