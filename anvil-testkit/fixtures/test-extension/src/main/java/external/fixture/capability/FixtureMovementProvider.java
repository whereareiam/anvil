package external.fixture.capability;

import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
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
	 * Sends moves and turns as this library's payloads; the library has no client physics and knows no position.
	 */
	private static final class FixtureMovement implements Movement {
		private final FixtureConnection connection;

		private FixtureMovement(FixtureConnection connection) {
			this.connection = connection;
		}

		@Override
		public void move(@NotNull Position position) {
			connection.send("move:" + position.getX() + ":" + position.getY() + ":" + position.getZ());
		}

		@Override
		public void physics(boolean enabled) {
			if (enabled) throw new IllegalStateException("The fixture library has no client physics");
		}

		@Override
		public boolean physics() {
			return false;
		}

		@Override
		public void input(@NotNull MovementInput input) {
			connection.send("input:" + input);
		}

		@Override
		public void look(float yaw, float pitch) {
			connection.send("look:" + yaw + ":" + pitch);
		}

		@Override
		public @NotNull Position position() {
			throw new IllegalStateException("The fixture library knows no position");
		}

		@Override
		public @NotNull Motion motion() {
			return new Motion(0.0, 0.0, 0.0);
		}

		@Override
		public void awaitTicks(int ticks) {
			throw new IllegalStateException("The fixture library runs no physics ticks");
		}

		@Override
		public void awaitTicks(int ticks, @NotNull Duration timeout) {
			awaitTicks(ticks);
		}
	}
}
