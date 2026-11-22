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
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final EntityId ENTITY_ID = new EntityId(7);

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final PlayerState state = new PlayerState(MinecraftVersion.parse("1.21.11"), player.protocol().player());

	PlayerStateTest() {
		PlayerStatePackets.follow(state, player.protocol());
		player.connect();
	}

	@Test
	void followsTheAbilitiesOfTheGameModeTheServerSets() {
		login(GameMode.CREATIVE);
		assertTrue(state.isMayFly());
		assertFalse(state.isFlying());

		player.deliver(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 3.0F));
		assertTrue(state.isSpectator());
		assertTrue(state.isFlying());

		player.deliver(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 0.0F));
		assertFalse(state.isMayFly());
		assertFalse(state.isFlying());
	}

	@Test
	void takesTheAbilitiesTheServerSends() {
		login(GameMode.SURVIVAL);

		player.deliver(ClientboundPlayerAbilities.builder().flying(true).mayFly(true).flyingSpeed(0.1F)
				.walkingSpeed(0.2F).build());

		assertTrue(state.isFlying());
		assertEquals(0.1F, state.getFlyingSpeed());
		assertEquals(0.2F, state.getWalkingSpeed());
	}

	@Test
	void readsTheDeathTheClientFollowsUntilTheRespawn() {
		login(GameMode.SURVIVAL);
		player.deliver(new ClientboundPlayerCombatKill(new EntityId(8), "Bob died"));
		assertFalse(state.isDead());

		player.deliver(new ClientboundSetHealth(0.0F, 20, 5.0F));
		assertTrue(state.isDead());
		respawn();
		assertFalse(state.isDead());

		player.deliver(new ClientboundPlayerCombatKill(ENTITY_ID, "Alice died"));
		assertTrue(state.isDead());
	}

	@Test
	void takesTheEffectsOfItsOwnEntityAndForgetsThemWhenItRespawns() {
		login(GameMode.SURVIVAL);
		player.deliver(effect(new EntityId(8), "minecraft:jump_boost"));
		player.deliver(effect(ENTITY_ID, "minecraft:levitation"));
		assertFalse(state.getEffects().has(MobEffect.JUMP_BOOST));
		assertTrue(state.getEffects().has(MobEffect.LEVITATION));

		respawn();

		assertFalse(state.getEffects().has(MobEffect.LEVITATION));
	}

	@Test
	void needsMoreThanSixFoodToSprintUnlessItMayFly() {
		login(GameMode.SURVIVAL);
		player.deliver(new ClientboundSetHealth(20.0F, 6, 0.0F));
		assertFalse(state.hasEnoughFood());

		player.deliver(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 1.0F));
		assertTrue(state.hasEnoughFood());
	}

	private void login(@NotNull GameMode mode) {
		player.deliver(ClientboundLogin.builder().entityId(ENTITY_ID).level(OVERWORLD).dimension(OVERWORLD)
				.dimensionType(DimensionType.known(OVERWORLD)).gameMode(mode).build());
	}

	private void respawn() {
		player.deliver(ClientboundRespawn.builder().dimension(OVERWORLD).dimensionType(DimensionType.known(OVERWORLD))
				.build());
	}

	private static @NotNull ClientboundUpdateMobEffect effect(@NotNull EntityId entity, @NotNull String effect) {
		return ClientboundUpdateMobEffect.builder().entityId(entity).effect(effect).amplifier(0).duration(100).build();
	}
}
