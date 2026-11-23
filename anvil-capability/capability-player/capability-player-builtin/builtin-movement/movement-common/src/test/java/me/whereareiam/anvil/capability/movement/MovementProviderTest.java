package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementProviderTest {
	@Test
	void describesTheMovementCapabilityForTheLibrariesWithAWorkerSide() {
		MovementProvider provider = new MovementProvider();

		assertEquals(MovementChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Movement.class, provider.capability());
	}

	@Test
	void sendsEachMoveThroughThePlayersChannelWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(MovementChannel.ID));
		channel.answer(MovementChannel.MOVE, position -> null);
		Movement movement = new MovementProvider().create(new TestProtocolPlayerCapabilityContext(channel));
		assertTrue(channel.requests().isEmpty());

		Position position = Position.builder().x(1).y(64).z(-2).yaw(90).pitch(10).onGround(true).build();
		movement.move(position);

		assertEquals(List.of(MovementChannel.MOVE.id()), channel.requests());
		assertEquals(List.of(position), channel.payloads());
	}
}
