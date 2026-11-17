package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks jumps, the flight the jump key toggles and the abilities the client tells the server when its flight changes.
 */
class JumpingTest {
	private static final MovementInput JUMP = MovementInput.builder().jump(true).build();

	@ParameterizedTest
	@ValueSource(strings = {"1.16.5", "1.20.6", "1.21.11"})
	void jumpsHigherWithJumpBoostUntilTheServerTakesItAway(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.effect(ClientboundUpdateMobEffect.builder().entityId(TestClient.ENTITY_ID).effect("minecraft:jump_boost")
				.amplifier(1).duration(200).build());
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);

		player.tick(JUMP);

		float power = 0.42F * 1.0F * 1.0F + 0.1F * (1 + 1.0F);
		assertEquals(65.0 + power, player.body().getPosition().y);
		for (int tick = 0; tick < 40 && !player.body().isOnGround(); tick++) player.tick(MovementInput.NONE);
		state.removeEffect(new ClientboundRemoveMobEffect(TestClient.ENTITY_ID, "minecraft:jump_boost"));
		for (int tick = 0; tick < 10; tick++) player.tick(MovementInput.NONE);
		player.tick(JUMP);
		assertEquals(65.0 + 0.42F, player.body().getPosition().y);
	}

	@Test
	void jumpsWithTheJumpStrengthTheServerSets() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		state.attributes(new ClientboundUpdateAttributes(TestClient.ENTITY_ID,
				List.of(new AttributeSnapshot("minecraft:jump_strength", 1.0, List.of()))));
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);

		player.tick(JUMP);

		assertEquals(66.0, player.body().getPosition().y);
	}

	@Test
	void togglesFlightWhenJumpIsPressedTwiceWithinSevenTicks() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.CREATIVE);
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);

		player.tick(JUMP);
		player.tick(MovementInput.NONE);
		assertFalse(state.isFlying());
		player.tick(JUMP);

		assertTrue(state.isFlying());
	}

	@Test
	void tellsTheServerEachFlightItTogglesInTheTickItTogglesIt() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.CREATIVE);
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);

		player.tick(JUMP);
		assertEquals(List.of(), player.body().abilityUpdates());
		player.tick(MovementInput.NONE);
		player.tick(JUMP);
		assertEquals(List.of(new ServerboundPlayerAbilities(true)), player.body().abilityUpdates());
		player.tick(JUMP);
		assertEquals(List.of(), player.body().abilityUpdates(), "a held key toggles nothing");
		player.tick(MovementInput.NONE);
		player.tick(JUMP);
		player.tick(MovementInput.NONE);
		player.tick(JUMP);

		assertFalse(state.isFlying());
		assertEquals(List.of(new ServerboundPlayerAbilities(false)), player.body().abilityUpdates());
	}

	@Test
	void tellsTheServerItLandedAndStoppedFlying() {
		TestClient client = new TestClient("1.20.6").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 66.0, 8.5);

		List<ServerboundPlayerAbilities> told = new ArrayList<>();
		for (int tick = 0; tick < 20 && state.isFlying(); tick++) {
			player.tick(MovementInput.builder().sneak(true).build());
			told.addAll(player.body().abilityUpdates());
		}

		assertFalse(state.isFlying());
		assertTrue(player.body().isOnGround());
		assertEquals(List.of(new ServerboundPlayerAbilities(false)), told);
	}

	@Test
	void tellsTheServerASpectatorFliesOnlyWhenItWasNotFlying() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SPECTATOR);
		PlayerSimulation player = client.simulation(state, 8.5, 70.0, 8.5);

		player.tick(MovementInput.NONE);
		assertEquals(List.of(), player.body().abilityUpdates(), "the game mode made the spectator fly already");
		state.abilities(ClientboundPlayerAbilities.builder().flying(false).mayFly(true).flyingSpeed(0.05F).build());
		player.tick(MovementInput.NONE);

		assertTrue(state.isFlying());
		assertEquals(List.of(new ServerboundPlayerAbilities(true)), player.body().abilityUpdates());
	}

	@Test
	void neverTogglesFlightWithoutTheAbility() {
		TestClient client = new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.SURVIVAL);
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);

		for (int tick = 0; tick < 6; tick++) player.tick(tick % 2 == 0 ? JUMP : MovementInput.NONE);

		assertFalse(state.isFlying());
		assertEquals(List.of(), player.body().abilityUpdates());
	}
}
