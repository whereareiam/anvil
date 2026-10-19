package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelMovementTest {
	private static final Position POSITION = Position.builder().x(1).y(64).z(-2).yaw(90).pitch(10).onGround(true).build();

	@Test
	void requestsEachActionFromTheWorker() {
		WorkerChannel channel = new WorkerChannel();
		Movement movement = new ChannelMovement(channel);

		movement.physics(true);
		movement.input(MovementInput.builder().forward(true).build());
		movement.look(45, 5);

		assertEquals(List.of(MovementChannel.PHYSICS.getId(), MovementChannel.INPUT.getId(), MovementChannel.LOOK.getId()),
				channel.requests);
		assertEquals(List.of(true, MovementInput.builder().forward(true).build(), new ViewRotation(45, 5)), channel.payloads);
	}

	@Test
	void readsThePositionMotionAndPhysicsFromTheWorker() {
		WorkerChannel channel = new WorkerChannel();
		channel.state = MovementState.builder().physics(true).ticks(3).position(POSITION).motion(new Motion(0, -0.0784, 0)).build();
		Movement movement = new ChannelMovement(channel);

		assertEquals(POSITION, movement.position());
		assertEquals(new Motion(0, -0.0784, 0), movement.motion());
		assertTrue(movement.physics());
	}

	@Test
	void refusesPhysicsWithTheWorkersReason() {
		WorkerChannel channel = new WorkerChannel();
		channel.state = channel.state.toBuilder().physicsUnavailable("No block data for Minecraft 26.2: missing").build();
		Movement movement = new ChannelMovement(channel);

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> movement.physics(true));

		assertEquals("Client physics is unavailable: No block data for Minecraft 26.2: missing", failure.getMessage());
		assertEquals(List.of(), channel.requests);
		movement.physics(false);
		assertEquals(List.of(MovementChannel.PHYSICS.getId()), channel.requests);
	}

	@Test
	void reportsThatTheClientDoesNotKnowWhereItIs() {
		WorkerChannel channel = new WorkerChannel();
		Movement movement = new ChannelMovement(channel);

		IllegalStateException failure = assertThrows(IllegalStateException.class, movement::position);

		assertTrue(failure.getMessage().contains("does not know where it is"), failure.getMessage());
	}

	@Test
	void waitsUntilTheClientRanTheTicks() {
		WorkerChannel channel = new WorkerChannel();
		channel.ticking = true;
		Movement movement = new ChannelMovement(channel);

		movement.awaitTicks(5, Duration.ofSeconds(1));

		assertEquals(6, channel.state.getTicks(), "the wait returned once the read after the start showed five more ticks");
		assertEquals("run 5 physics ticks", channel.waited);
		assertThrows(IllegalArgumentException.class, () -> movement.awaitTicks(0));
	}

	/**
	 * A worker whose client runs one tick on each read while it ticks.
	 */
	private static final class WorkerChannel implements ProtocolChannel {
		private final List<String> requests = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private MovementState state = MovementState.builder().motion(new Motion(0, 0, 0)).build();
		private boolean ticking;
		private @Nullable String waited;

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			if (operation == MovementChannel.STATE) {
				MovementState current = state;
				if (ticking) state = current.toBuilder().ticks(current.getTicks() + 1).build();
				return (R) current;
			}

			requests.add(operation.getId());
			payloads.add(request);
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> event, @NotNull Consumer<E> listener) {
			return () -> { };
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			waited = description;
			for (int check = 0; check < 100; check++) if (condition.getAsBoolean()) return;

			throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(MovementChannel.ID);
		}
	}
}
