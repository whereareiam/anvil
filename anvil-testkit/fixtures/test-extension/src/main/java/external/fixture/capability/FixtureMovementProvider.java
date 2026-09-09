package external.fixture.capability;

import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Translates the portable Movement API into this backend's own payload representation.
 */
public final class FixtureMovementProvider implements ProtocolPlayerCapabilityProvider<Movement> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id("external.fixture.movement")
				.requiredCapability(Session.class)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("fixture");
	}

	@Override
	public @NotNull Class<Movement> capability() {
		return Movement.class;
	}

	@Override
	public @NotNull Movement create(@NotNull ProtocolPlayerCapabilityContext context) {
		context.requireCapability(Session.class);
		FixtureConnection connection = context.requireService(FixtureConnection.class);
		return position -> connection.send("move:" + position.getX() + ":" + position.getY() + ":" + position.getZ());
	}
}
