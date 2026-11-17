package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelClientPhysicsTest {
	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(MovementChannel.ID));
	private final AtomicReference<MovementState> state = new AtomicReference<>(state(true, 10, null));
	private final ChannelClientPhysics physics = new ChannelClientPhysics(channel, "Alice");

	ChannelClientPhysicsTest() {
		channel.answer(MovementChannel.STATE, ignored -> state.get());
	}

	@Test
	void turnsPhysicsOnAndOffThroughTheWorker() {
		channel.answer(MovementChannel.PHYSICS, enabled -> new PhysicsChange(null));

		physics.enable();
		physics.disable();

		assertEquals(List.of(true, false), channel.payloads());
	}

	@Test
	void reportsAReleaseThatCannotSimulateAsUnavailableWithTheWorkersReason() {
		channel.answer(MovementChannel.PHYSICS, enabled -> new PhysicsChange("no block data for protocol 999"));

		var failure = assertThrows(CapabilityUnavailableException.class, physics::enable);

		assertTrue(failure.getMessage().contains("no block data for protocol 999"), failure.getMessage());
		assertEquals(List.of("movement.physics"), channel.requests());
	}

	@Test
	void readsWhetherPhysicsIsOnWhyItTurnedOffAndTheMotion() {
		assertTrue(physics.isEnabled());
		assertEquals(Optional.empty(), physics.failure());

		state.set(state(false, 12, "tick failed: boom"));

		assertEquals(Optional.of("tick failed: boom"), physics.failure());
		assertEquals(new Motion(0, -0.08, 0), physics.motion());
	}

	@Test
	void sendsTheHeldKeys() {
		channel.answer(MovementChannel.INPUT, input -> null);
		MovementInput forward = MovementInput.builder().forward(true).build();

		physics.input(forward);

		assertEquals(List.of(forward), channel.payloads());
	}

	@Test
	void waitsUntilTheTicksHavePassed() {
		channel.meanwhile(() -> state.set(state(true, 15, null)));

		physics.awaitTicks(5, Duration.ofSeconds(1));
	}

	@Test
	void stopsWaitingWithTheReasonWhenPhysicsTurnsOff() {
		channel.meanwhile(() -> state.set(state(false, 11, "tick failed: boom")));

		var failure = assertThrows(IllegalStateException.class, () -> physics.awaitTicks(5, Duration.ofSeconds(1)));

		assertTrue(failure.getMessage().contains("tick failed: boom"), failure.getMessage());
	}

	@Test
	void refusesToWaitWhilePhysicsIsOff() {
		state.set(state(false, 10, null));

		assertThrows(IllegalStateException.class, () -> physics.awaitTicks(1, Duration.ofSeconds(1)));
		assertThrows(IllegalArgumentException.class, () -> physics.awaitTicks(0, Duration.ofSeconds(1)));
	}

	private static MovementState state(boolean on, long ticks, @Nullable String failure) {
		return MovementState.builder()
				.physics(on)
				.ticks(ticks)
				.failure(failure)
				.motion(new Motion(0, -0.08, 0))
				.build();
	}
}
