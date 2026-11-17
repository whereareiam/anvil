package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
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
 * Checks the flight the jump key toggles and steers, its landing, and the abilities the client tells the server when
 * its flight changes.
 */
class FlightTest {
	private static final MovementInput JUMP = MovementInput.builder().jump(true).build();
	private static final MovementInput SNEAK = MovementInput.builder().sneak(true).build();

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

		player.tick(JUMP);
		assertEquals(100.0 + 1 * 0.05F * 3.0F, player.body().getPosition().y);
		assertEquals((1 * 0.05F * 3.0F) * 0.6, player.body().getDeltaMovement().y);

		for (int tick = 0; tick < 10; tick++) player.tick(SNEAK);
		assertTrue(player.body().getPosition().y < 100.0);
		assertTrue(state.isFlying());
	}

	@Test
	void togglesFlightWhenJumpIsPressedTwiceWithinSevenTicks() {
		TestClient client = floored();
		PlayerState state = client.state(GameMode.CREATIVE);
		PlayerSimulation player = standing(client, state);

		player.tick(JUMP);
		player.tick(MovementInput.NONE);
		assertFalse(state.isFlying());
		player.tick(JUMP);

		assertTrue(state.isFlying());
	}

	@Test
	void tellsTheServerEachFlightItTogglesInTheTickItTogglesIt() {
		TestClient client = floored();
		PlayerState state = client.state(GameMode.CREATIVE);
		PlayerSimulation player = standing(client, state);

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

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void landsAndTellsTheServerItStoppedFlying(@NotNull String version) {
		TestClient client = new TestClient(version).join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		PlayerState state = client.state(GameMode.CREATIVE);
		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.05F).build());
		PlayerSimulation player = client.simulation(state, 8.5, 66.0, 8.5);

		List<ServerboundPlayerAbilities> told = new ArrayList<>();
		for (int tick = 0; tick < 20 && state.isFlying(); tick++) {
			player.tick(SNEAK);
			told.addAll(player.body().abilityUpdates());
		}

		assertFalse(state.isFlying());
		assertTrue(player.body().isOnGround());
		assertEquals(List.of(new ServerboundPlayerAbilities(false)), told);
	}

	@Test
	void tellsTheServerASpectatorFliesOnlyWhenItWasNotFlying() {
		TestClient client = floored();
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
		TestClient client = floored();
		PlayerState state = client.state(GameMode.SURVIVAL);
		PlayerSimulation player = standing(client, state);

		for (int tick = 0; tick < 6; tick++) player.tick(tick % 2 == 0 ? JUMP : MovementInput.NONE);

		assertFalse(state.isFlying());
		assertEquals(List.of(), player.body().abilityUpdates());
	}

	private static @NotNull TestClient floored() {
		return new TestClient("1.21.11").join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
	}

	private static @NotNull PlayerSimulation standing(@NotNull TestClient client, @NotNull PlayerState state) {
		PlayerSimulation player = client.simulation(state, 8.5, 65.0, 8.5);
		PlayerSimulationTest.settle(player);
		return player;
	}
}
