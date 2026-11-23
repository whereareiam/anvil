package me.whereareiam.anvil.capability.movement.java.physics.player.input;

import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the input the held keys become and when they sprint.
 */
class KeyInputTest {
	private static final MovementInput DIAGONAL = MovementInput.builder().forward(true).left(true).build();
	private static final MovementInput SPRINT = MovementInput.builder().forward(true).sprint(true).build();

	@Test
	void normalizesADiagonalToTheCornerOfTheUnitSquareFrom1_21_5() {
		PlayerSimulation player = standing(new TestClient("1.21.11"), GameMode.SURVIVAL);

		player.tick(DIAGONAL);

		float unit = 1.0F / (float) Math.sqrt(2.0F);
		float scaled = unit * 0.98F;
		float length = (float) Math.sqrt(scaled * scaled + scaled * scaled);
		float corner = scaled * (1.0F / length) * Math.min(length * (float) Math.sqrt(2.0F), 1.0F);
		assertEquals(corner, player.body().xxa());
		assertEquals(corner, player.body().zza());
	}

	@Test
	void keepsTheImpulsesOfBothKeysBefore1_21_5() {
		PlayerSimulation player = standing(new TestClient("1.20.6"), GameMode.SURVIVAL);

		player.tick(DIAGONAL);

		assertEquals(1.0F * 0.98F, player.body().xxa());
		assertEquals(1.0F * 0.98F, player.body().zza());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void sprintsWithTheSprintKeyButNotBlindOrHungry(@NotNull String version) {
		PlayerSimulation sighted = standing(new TestClient(version), GameMode.SURVIVAL);
		TestClient blinded = new TestClient(version);
		PlayerState blind = state(blinded);
		blind.effect(ClientboundUpdateMobEffect.builder().entityId(TestClient.ENTITY_ID).effect("minecraft:blindness")
				.amplifier(0).duration(200).build());
		TestClient starved = new TestClient(version);
		PlayerState hungry = state(starved);
		hungry.health(new ClientboundSetHealth(20.0F, 6, 0.0F));

		sighted.tick(SPRINT);
		PlayerSimulation blindPlayer = settle(blinded.simulation(blind, 8.5, 65.0, 8.5));
		blindPlayer.tick(SPRINT);
		PlayerSimulation hungryPlayer = settle(starved.simulation(hungry, 8.5, 65.0, 8.5));
		hungryPlayer.tick(SPRINT);

		assertTrue(sighted.body().sprinting());
		assertFalse(blindPlayer.body().sprinting());
		assertFalse(hungryPlayer.body().sprinting());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.20.6", "1.21.11"})
	void sprintsOnASecondPressOfForwardWithinTheSprintWindow(@NotNull String version) {
		PlayerSimulation player = standing(new TestClient(version), GameMode.SURVIVAL);
		MovementInput forward = MovementInput.builder().forward(true).build();

		player.tick(forward);
		player.tick(MovementInput.NONE);
		assertFalse(player.body().sprinting());
		player.tick(forward);

		assertTrue(player.body().sprinting());
	}

	private static @NotNull PlayerSimulation standing(@NotNull TestClient client, @NotNull GameMode mode) {
		return settle(client.join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send()
				.simulation(client.state(mode), 8.5, 65.0, 8.5));
	}

	private static @NotNull PlayerState state(@NotNull TestClient client) {
		client.join().load(0, 0).floor(0, 15, 64, 0, 15, "minecraft:stone").send();
		return client.state(GameMode.SURVIVAL);
	}

	private static @NotNull PlayerSimulation settle(@NotNull PlayerSimulation player) {
		for (int tick = 0; tick < 5; tick++) player.tick(MovementInput.NONE);
		return player;
	}
}
