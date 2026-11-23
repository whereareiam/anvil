package external.fixture.capability;

import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.ClientPhysics;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Translates the portable Movement API into this library's own payload representation.
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
	public @NotNull Set<String> supportedLibraries() {
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
		return new FixtureMovement(connection);
	}

	/**
	 * Sends moves and looks as this library's payloads; the library simulates no client, so it has no physics.
	 */
	private record FixtureMovement(FixtureConnection connection) implements Movement {
		@Override
		public void move(@NotNull Position position) {
			connection.send("move:" + position.x() + ":" + position.y() + ":" + position.z());
		}

		@Override
		public void look(float yaw, float pitch) {
			connection.send("look:" + yaw + ":" + pitch);
		}

		@Override
		public @NotNull Optional<Position> position() {
			return Optional.empty();
		}

		@Override
		public @NotNull ClientPhysics physics() {
			return NoPhysics.INSTANCE;
		}
	}

	/**
	 * The physics of a library that simulates no client: it cannot be turned on.
	 */
	private enum NoPhysics implements ClientPhysics {
		INSTANCE;

		private static final String REASON = "the fixture library simulates no client physics";

		@Override
		public void enable() {
			throw new CapabilityUnavailableException(REASON);
		}

		@Override
		public void disable() {
		}

		@Override
		public boolean enabled() {
			return false;
		}

		@Override
		public @NotNull Optional<String> failure() {
			return Optional.empty();
		}

		@Override
		public void input(@NotNull MovementInput input) {
			throw new CapabilityUnavailableException(REASON);
		}

		@Override
		public @NotNull Motion motion() {
			return new Motion(0, 0, 0);
		}

		@Override
		public void awaitTicks(int ticks, @NotNull Duration timeout) {
			throw new IllegalStateException(REASON);
		}
	}
}
