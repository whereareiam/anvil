package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the travel the server's state changes: flight, effects and attributes.
 */
class TravelTest {
	private static final MovementInput FORWARD = MovementInput.builder().forward(true).build();

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.21.1", "1.21.11"})
	void hoversInCreativeFlightAndRisesAndSinksWithJumpAndSneak(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F)
				.walkingSpeed(0.1F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 8.5);

		for (int tick = 0; tick < 10; tick++) player.tick(MovementInput.NONE);
		assertEquals(100.0, player.body().getPosition().y);

		player.tick(MovementInput.builder().jump(true).build());
		assertEquals(100.0 + 1 * 0.05F * 3.0F, player.body().getPosition().y);
		assertEquals((1 * 0.05F * 3.0F) * 0.6, player.body().getDeltaMovement().y);

		for (int tick = 0; tick < 10; tick++) player.tick(MovementInput.builder().sneak(true).build());
		assertTrue(player.body().getPosition().y < 100.0);
		assertTrue(state.isFlying());
	}

	@Test
	void fliesFasterThanItWalksAndTwiceAsFastSprinting() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).load(0, 1).load(0, 2).send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 2.5);

		player.tick(FORWARD);

		assertEquals(2.5 + 0.98F * 0.05F, player.body().getPosition().z, 1.0E-9);
	}

	@Test
	void risesWithLevitationAndStopsCountingItsFall() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.effect(ClientboundUpdateMobEffect.builder().entityId(TestClient.ENTITY_ID).effect("minecraft:levitation")
				.amplifier(0).duration(200).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 8.5);

		player.tick(MovementInput.NONE);
		assertEquals((0.0 + (0.05 * 1 - 0.0) * 0.2) * 0.98F, player.body().getDeltaMovement().y);
		for (int tick = 0; tick < 40; tick++) player.tick(MovementInput.NONE);

		assertTrue(player.body().getPosition().y > 101.0, "it rose to " + player.body().getPosition());
		assertTrue(player.body().getDeltaMovement().y < 0.05);
		assertEquals(0.0, player.body().getFallDistance());
	}

	@Test
	void fallsSlowlyWithSlowFalling() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.effect(ClientboundUpdateMobEffect.builder().entityId(TestClient.ENTITY_ID).effect("minecraft:slow_falling")
				.amplifier(0).duration(200).build());
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 8.5);

		player.tick(MovementInput.NONE);
		player.tick(MovementInput.NONE);

		assertEquals(((0.0 - 0.01) * 0.98F - 0.01) * 0.98F, player.body().getDeltaMovement().y);
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

	@Test
	void fallsWithTheGravityAttribute() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.attributes(new ClientboundUpdateAttributes(TestClient.ENTITY_ID,
				List.of(new AttributeSnapshot("minecraft:gravity", 0.04, List.of()))));
		PlayerSimulation player = client.simulation(state, 8.5, 100.0, 8.5);

		player.tick(MovementInput.NONE);

		assertEquals((0.0 - 0.04) * 0.98F, player.body().getDeltaMovement().y);
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

	@Test
	void stopsFlyingWhenItLandsOutsideSpectator() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 65.5, 8.5);

		for (int tick = 0; tick < 10 && state.isFlying(); tick++) player.tick(MovementInput.builder().sneak(true).build());

		assertFalse(state.isFlying());
		assertTrue(player.body().isOnGround());
	}
}
