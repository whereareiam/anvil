package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the push of flowing lava, which the dimension type's fast lava makes stronger whatever the type's key.
 */
class FluidStateTest {
	private static final DimensionType OVERWORLD = Objects.requireNonNull(DimensionType.known("minecraft:overworld"));

	@Test
	void flowingLavaPushesHarderInATypeWhoseLavaIsFastWhateverItsKey() {
		double fast = lavaPush(OVERWORLD.toBuilder().key("example:hot").fastLava(true).build());
		double slow = lavaPush(OVERWORLD.toBuilder().key("minecraft:the_nether").fastLava(false).build());

		assertTrue(slow > 0.0, "slow lava pushes the player along its flow");
		assertEquals(0.007 / 0.0045000000000000005, fast / slow, 1.0E-9);
	}

	/**
	 * Lets a player at rest stand one tick in lava flowing east and returns its eastward velocity afterwards: slow lava
	 * pushes a player at rest by the least push of {@code 0.0045}, fast lava by {@code 0.007}.
	 */
	private static double lavaPush(@NotNull DimensionType type) {
		TestClient client = new TestClient("1.21.11", type).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone");
		for (int x = 6; x <= 12; x++)
			for (int z = 6; z <= 10; z++)
				client.set(x, 65, z, "minecraft:lava", Map.of("level", String.valueOf(x - 6)));
		client.send();
		PlayerSimulation player = client.simulation(client.state(GameMode.SURVIVAL), 8.5, 65.0, 8.5);
		player.body().setFirstTick(false);
		player.body().setOnGround(true);

		player.tick(MovementInput.NONE);

		return player.body().getDeltaMovement().x;
	}
}
