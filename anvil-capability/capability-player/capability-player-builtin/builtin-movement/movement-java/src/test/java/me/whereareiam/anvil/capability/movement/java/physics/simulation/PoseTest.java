package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the player's pose and the dimensions it gives.
 */
class PoseTest {
	@Test
	void crouchesWhileSneakingAndStandsUpAfter() {
		PlayerSimulation player = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.send().simulation(8.5, 65.0, 8.5);

		player.tick(MovementInput.builder().sneak(true).build());
		assertEquals(1.5F, player.body().getHeight());
		assertEquals(1.27F, player.body().getEyeHeight());

		player.tick(MovementInput.NONE);
		assertEquals(1.8F, player.body().getHeight());
	}

	@Test
	void crawlsWhereItCannotEvenCrouch() {
		PlayerSimulation player = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone")
				.floor(0, 15, 66, 0, 15, "minecraft:stone").send().simulation(8.5, 65.0, 8.5);

		player.tick(MovementInput.NONE);

		assertEquals(0.6F, player.body().getHeight());
		assertEquals(0.4F, player.body().getEyeHeight());
	}

	@Test
	void growsWithTheScaleTheServerSets() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		state.attributes(new ClientboundUpdateAttributes(TestClient.ENTITY_ID,
				List.of(new AttributeSnapshot("minecraft:scale", 2.0, List.of()))));

		player.tick(MovementInput.NONE);

		assertEquals(1.2F, player.body().getWidth());
		assertEquals(3.6F, player.body().getHeight());
		assertEquals(3.6F, (float) (player.body().getBox().maxY - player.body().getBox().minY));
	}
}
