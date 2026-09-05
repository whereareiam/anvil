package me.whereareiam.anvil.capability.movement.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for absolute movement and view changes.
 */
public final class McProtocolMovementProvider implements PlayerCapabilityProvider<Movement> {
	static final String ID = "me.whereareiam.anvil.movement";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.supportedProtocolId("mcprotocol")
				.requiredCapability(Session.class)
				.build();
	}

	@Override
	public @NotNull Class<Movement> capability() {
		return Movement.class;
	}

	@Override
	public @NotNull Movement create(@NotNull PlayerCapabilityContext context) {
		context.requireCapability(Session.class);
		ProtocolPlayerConnection connection = context.requireService(ProtocolPlayerConnection.class);
		if (!connection.workerCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return position -> connection.execute("movement.move", arguments -> {
			arguments.put("x", position.getX());
			arguments.put("y", position.getY());
			arguments.put("z", position.getZ());
			arguments.put("yaw", position.getYaw());
			arguments.put("pitch", position.getPitch());
			arguments.put("onGround", position.isOnGround());
		});
	}
}
