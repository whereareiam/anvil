package me.whereareiam.anvil.capability.movement.java.physics.server;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {
	private static final String OVERWORLD = "minecraft:overworld";

	private final PlayerState state = new PlayerState(MinecraftVersion.parse("1.21.11"));

	@Test
	void followsTheAbilitiesOfTheGameModeTheServerSets() {
		login(GameMode.CREATIVE);
		assertTrue(state.mayFly());
		assertFalse(state.flying());

		state.gameEvent(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 3.0F));
		assertTrue(state.isSpectator());
		assertTrue(state.flying());

		state.gameEvent(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 0.0F));
		assertFalse(state.mayFly());
		assertFalse(state.flying());
	}

	@Test
	void takesTheAbilitiesTheServerSends() {
		login(GameMode.SURVIVAL);

		state.abilities(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.1F)
				.walkingSpeed(0.2F).build());

		assertTrue(state.flying());
		assertEquals(0.1F, state.flyingSpeed());
		assertEquals(0.2F, state.walkingSpeed());
	}

	@Test
	void diesAtNoHealthOrItsOwnCombatKillAndLivesAgainAfterARespawn() {
		login(GameMode.SURVIVAL);
		state.combatKill(new ClientboundPlayerCombatKill(new EntityId(8), "Bob died"));
		assertFalse(state.dead());

		state.health(new ClientboundSetHealth(0.0F, 20, 5.0F));
		assertTrue(state.dead());
		state.respawn(ClientboundRespawn.builder().dimension(OVERWORLD).dimensionType(DimensionType.known(OVERWORLD))
				.build());
		assertFalse(state.dead());

		state.combatKill(new ClientboundPlayerCombatKill(new EntityId(7), "Alice died"));
		assertTrue(state.dead());
	}

	@Test
	void forgetsTheEffectsOfTheOldPlayerWhenItRespawns() {
		login(GameMode.SURVIVAL);
		state.effect(ClientboundUpdateMobEffect.builder().entityId(new EntityId(7)).effect("minecraft:levitation").amplifier(0)
				.duration(100).build());
		assertTrue(state.effects().has(MobEffect.LEVITATION));

		state.respawn(ClientboundRespawn.builder().dimension(OVERWORLD).dimensionType(DimensionType.known(OVERWORLD))
				.build());

		assertFalse(state.effects().has(MobEffect.LEVITATION));
	}

	@Test
	void needsMoreThanSixFoodToSprintUnlessItMayFly() {
		login(GameMode.SURVIVAL);
		state.health(new ClientboundSetHealth(20.0F, 6, 0.0F));
		assertFalse(state.hasEnoughFood());

		state.gameEvent(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 1.0F));
		assertTrue(state.hasEnoughFood());
	}

	@Test
	void forgetsItsEntityWhenTheConnectionEnds() {
		login(GameMode.SURVIVAL);

		state.disconnected();

		assertNull(state.entityId());
		assertFalse(state.own(new EntityId(7)));
	}

	private void login(@NotNull GameMode mode) {
		state.login(ClientboundLogin.builder().entityId(new EntityId(7)).level(OVERWORLD).dimension(OVERWORLD)
				.dimensionType(DimensionType.known(OVERWORLD)).gameMode(mode).build());
	}
}
