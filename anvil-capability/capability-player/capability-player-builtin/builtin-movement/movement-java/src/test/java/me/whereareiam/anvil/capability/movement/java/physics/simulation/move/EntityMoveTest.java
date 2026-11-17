package me.whereareiam.anvil.capability.movement.java.physics.simulation.move;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.api.type.world.GameEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how the player moves against blocks for what the server set of it.
 */
class EntityMoveTest {
	private static final MovementInput FORWARD = MovementInput.builder().forward(true).build();

	@Test
	void passesThroughBlocksAsASpectator() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone");
		for (int y = 65; y < 68; y++) client.floor(0, 15, y, 10, 10, "minecraft:stone");
		PlayerState state = client.send().state(GameMode.SURVIVAL);
		state.gameEvent(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 3.0F));
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);

		for (int tick = 0; tick < 60; tick++) player.tick(FORWARD);

		assertTrue(player.body().getPosition().z > 11.0, "the spectator passed the wall at " + player.body().getPosition());
		assertFalse(player.body().isOnGround());
		assertFalse(player.body().isHorizontalCollision());
	}

	@Test
	void stopsAtTheWallOutsideSpectator() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone");
		for (int y = 65; y < 68; y++) client.floor(0, 15, y, 10, 10, "minecraft:stone");
		PlayerSimulation player = client.send().simulation(8.5, 65.0, 8.5);

		for (int tick = 0; tick < 60; tick++) player.tick(FORWARD);

		assertEquals(10.0 - 0.3F, player.body().getPosition().z, 1.0E-6);
		assertTrue(player.body().isHorizontalCollision());
	}

	@Test
	void stepsUpAsHighAsTheStepHeightTheServerSets() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.floor(0, 15, 65, 10, 15, "minecraft:stone").send();
		PlayerSimulation plain = client.simulation(4.5, 65.0, 8.5);
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.attributes(new ClientboundUpdateAttributes(TestClient.ENTITY_ID,
				List.of(new AttributeSnapshot("minecraft:step_height", 1.0, List.of()))));
		PlayerSimulation stepping = client.simulation(state, 12.5, 65.0, 8.5);

		for (int tick = 0; tick < 30; tick++) {
			plain.tick(FORWARD);
			stepping.tick(FORWARD);
		}

		assertEquals(65.0, plain.body().getPosition().y);
		assertEquals(66.0, stepping.body().getPosition().y);
	}
}
