package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.api.type.world.GameEventType;
import me.whereareiam.anvil.protocol.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.protocol.java.physics.living.effect.Effects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the server set of the player's own entity, as the vanilla client follows it: the entity's ID from the login,
 * the game mode of the login, each respawn and each {@code CHANGE_GAME_MODE} game event, which set the abilities it
 * implies ({@code GameType.updatePlayerAbilities}), the abilities the server sends ({@code handlePlayerAbilities}), the
 * food level, the attributes and effects of the own entity, and its death, from a health of zero or less or the
 * player's combat kill until it respawns. A login and a respawn make a new player, as the vanilla client creates one:
 * default abilities and food, no effects, and, after a respawn, the attributes' base values without their modifiers.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
@Getter
public final class PlayerState {
	private static final float DEFAULT_FLYING_SPEED = 0.05F;
	private static final float DEFAULT_WALKING_SPEED = 0.1F;
	private static final int DEFAULT_FOOD = 20;

	private final @NotNull AttributeMap attributes;
	private final @NotNull Effects effects = new Effects();
	/**
	 * The protocol ID of the player's own entity, which the server may choose negative, or {@code null} until the
	 * player joined a level on its current connection.
	 */
	private @Nullable EntityId entityId;
	private @NotNull GameMode gameMode = GameMode.SURVIVAL;
	/**
	 * Whether the player flies, which the client also changes itself when it toggles flight or lands.
	 */
	@Setter
	private boolean flying;
	private boolean mayFly;
	private float flyingSpeed = DEFAULT_FLYING_SPEED;
	/**
	 * The walking speed the server sends, which the vanilla client keeps but never moves by: its movement speed is the
	 * attribute's.
	 */
	private float walkingSpeed = DEFAULT_WALKING_SPEED;
	@Getter(AccessLevel.NONE)
	private int food = DEFAULT_FOOD;
	private boolean dead;

	/**
	 * Creates the state of a player that has not joined a level.
	 *
	 * @param version the client's Minecraft version
	 */
	public PlayerState(@NotNull MinecraftVersion version) {
		this.attributes = new AttributeMap(version, PlayerAttributes.supplier());
	}

	/**
	 * Follows a login: a new player with the login's entity ID and game mode.
	 *
	 * @param login the login
	 */
	public void login(@NotNull ClientboundLogin login) {
		entityId = login.getEntityId();
		attributes.reset();
		renew(login.getGameMode());
	}

	/**
	 * Follows a respawn: a new player in the respawn's game mode, alive again.
	 *
	 * @param respawn the respawn
	 */
	public void respawn(@NotNull ClientboundRespawn respawn) {
		attributes.keepBaseValues();
		renew(respawn.getGameMode());
	}

	/**
	 * Forgets the entity, whose connection ended.
	 */
	public void disconnected() {
		entityId = null;
	}

	/**
	 * Follows a game event, of which only a changed game mode concerns movement; the event's value rounds to the game
	 * mode's ID and an unknown ID is survival, as {@code handleGameEvent} and {@code GameType.byId} read it.
	 *
	 * @param event the event
	 */
	public void gameEvent(@NotNull ClientboundGameEvent event) {
		if (event.getEvent() != GameEventType.CHANGE_GAME_MODE) return;

		int id = (int) Math.floor(event.getValue() + 0.5F);
		gameMode(id >= 0 && id < GameMode.values().length ? GameMode.values()[id] : GameMode.SURVIVAL);
	}

	/**
	 * Takes the abilities the server sends.
	 *
	 * @param abilities the abilities
	 */
	public void abilities(@NotNull ClientboundPlayerAbilities abilities) {
		flying = abilities.isFlying();
		mayFly = abilities.isMayFly();
		flyingSpeed = abilities.getFlyingSpeed();
		walkingSpeed = abilities.getWalkingSpeed();
	}

	/**
	 * Takes the player's health and food; a health of zero or less is death.
	 *
	 * @param health the health
	 */
	public void health(@NotNull ClientboundSetHealth health) {
		food = health.getFood();
		if (health.getHealth() <= 0.0F) dead = true;
	}

	/**
	 * Follows the combat kill of an entity, which is the player's death when it names the player's own entity.
	 *
	 * @param kill the kill
	 */
	public void combatKill(@NotNull ClientboundPlayerCombatKill kill) {
		if (own(kill.getPlayerId())) dead = true;
	}

	/**
	 * Takes the attributes the server sends for an entity, when it is the player's own.
	 *
	 * @param update the attributes
	 */
	public void attributes(@NotNull ClientboundUpdateAttributes update) {
		if (own(update.getEntityId())) attributes.update(update.getAttributes());
	}

	/**
	 * Takes an effect the server gives an entity, when it is the player's own.
	 *
	 * @param effect the effect
	 */
	public void effect(@NotNull ClientboundUpdateMobEffect effect) {
		if (own(effect.getEntityId())) effects.add(effect.getEffect(), effect.getAmplifier());
	}

	/**
	 * Takes away an effect of an entity, when it is the player's own.
	 *
	 * @param effect the removal
	 */
	public void removeEffect(@NotNull ClientboundRemoveMobEffect effect) {
		if (own(effect.getEntityId())) effects.remove(effect.getEffect());
	}

	/**
	 * Tells whether an entity ID is the player's own.
	 *
	 * @param id the entity's ID
	 * @return whether it is the player's entity
	 */
	public boolean own(@NotNull EntityId id) {
		return id.equals(entityId);
	}

	/**
	 * Tells whether the player is a spectator, which flies through blocks ({@code Player.noPhysics}).
	 *
	 * @return whether it spectates
	 */
	public boolean isSpectator() {
		return gameMode == GameMode.SPECTATOR;
	}

	/**
	 * Tells whether the player has the food to sprint ({@code hasEnoughFoodToDoExhaustiveManoeuvres}): more than six
	 * food points, or abilities that let it fly.
	 *
	 * @return whether it may sprint for its food
	 */
	public boolean hasEnoughFood() {
		return food > 6.0F || mayFly;
	}

	private void renew(@NotNull GameMode mode) {
		effects.clear();
		dead = false;
		food = DEFAULT_FOOD;
		flyingSpeed = DEFAULT_FLYING_SPEED;
		walkingSpeed = DEFAULT_WALKING_SPEED;
		flying = false;
		gameMode(mode);
	}

	/**
	 * Sets the game mode and the abilities it implies, as {@code GameType.updatePlayerAbilities} does.
	 */
	private void gameMode(@NotNull GameMode mode) {
		gameMode = mode;
		mayFly = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
		if (mode == GameMode.SPECTATOR) flying = true;
		if (!mayFly) flying = false;
	}
}
