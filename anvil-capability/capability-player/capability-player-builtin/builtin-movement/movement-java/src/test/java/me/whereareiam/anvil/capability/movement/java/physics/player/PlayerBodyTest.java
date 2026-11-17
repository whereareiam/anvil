package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.api.type.world.GameEventType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how the player answers the physics kit as {@code Player} and {@code LocalPlayer} do: a spectator moves through
 * blocks, its speeds come from what the server set, and only its own entity's state counts.
 */
class PlayerBodyTest {
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
	void fliesAtTheFlyingSpeedOfItsAbilities() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).load(0, 1).load(0, 2).send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 2.5);

		player.tick(FORWARD);

		assertEquals(2.5 + 0.98F * 0.05F, player.body().getPosition().z, 1.0E-9);
	}

	@Test
	void ignoresTheEffectsOfOtherEntities() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.effect(ClientboundUpdateMobEffect.builder().entityId(new EntityId(TestClient.ENTITY_ID.getValue() + 1))
				.effect("minecraft:slow_falling").amplifier(0).duration(200).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 8.5);

		player.tick(MovementInput.NONE);

		assertEquals((0.0 - 0.08) * 0.98F, player.body().getDeltaMovement().y);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void walksAtTheMovementSpeedTheServerSets(@NotNull String version) {
		double plain = firstStep(version, List.of());
		double boosted = firstStep(version, List.of(new AttributeModifier("minecraft:effect.speed", 0.5,
				AttributeOperation.ADD_MULTIPLIED_TOTAL)));

		assertEquals(1.5, boosted / plain, 1.0E-6);
	}

	private static double firstStep(@NotNull String version, @NotNull List<AttributeModifier> modifiers) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.attributes(new ClientboundUpdateAttributes(TestClient.ENTITY_ID,
				List.of(new AttributeSnapshot("minecraft:movement_speed", 0.1F, modifiers))));
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 2.5);
		PlayerSimulationTest.settle(player);

		player.tick(FORWARD);
		return player.body().getPosition().z - 2.5;
	}
}
