package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChannelMovementTest {
	private static final Position SPAWN = Position.builder().x(0.5).y(64).z(0.5).onGround(true).build();

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(MovementChannel.ID));
	private final ChannelMovement movement = new ChannelMovement(channel, "Alice");

	@Test
	void sendsTheMoveAndTheLookToTheWorker() {
		channel.answer(MovementChannel.MOVE, position -> null);
		channel.answer(MovementChannel.LOOK, rotation -> null);

		movement.move(SPAWN);
		movement.look(90, -15);

		assertEquals(List.of(SPAWN, new ViewRotation(90, -15)), channel.payloads());
	}

	@Test
	void readsWhereTheClientIsOnlyOnceTheServerPlacedIt() {
		channel.answer(MovementChannel.STATE, ignored -> state(null));
		assertEquals(Optional.empty(), movement.position());

		channel.answer(MovementChannel.STATE, ignored -> state(SPAWN));
		assertEquals(Optional.of(SPAWN), movement.position());
	}

	@Test
	void keepsOnePhysicsForThePlayer() {
		assertSame(movement.physics(), movement.physics());
	}

	private static MovementState state(Position position) {
		return MovementState.builder().position(position).motion(new Motion(0, 0, 0)).build();
	}
}
