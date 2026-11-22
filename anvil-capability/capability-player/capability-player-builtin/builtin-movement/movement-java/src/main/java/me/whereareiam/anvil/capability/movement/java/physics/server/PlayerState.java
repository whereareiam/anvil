package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.effect.Effects;
import me.whereareiam.anvil.edition.java.protocol.LocalPlayer;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import org.jetbrains.annotations.NotNull;

/**
 * What the server set of the player's own entity that only client physics reads, as the vanilla client follows it: the
 * abilities the game mode implies ({@code GameType.updatePlayerAbilities}) at the login, each respawn and each change of
 * the game mode, the abilities the server sends ({@code handlePlayerAbilities}), the food level, and the attributes and
 * effects of the own entity. The client's own player, every Java player's, keeps the entity's ID, its game mode and its
 * death, which this state reads from it after the client followed the same packet. A login and a respawn make a new
 * player, as the vanilla client creates one: default abilities and food, no effects, and, after a respawn, the
 * attributes' base values without their modifiers.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks, as the client's lock does.</p>
 */
@Getter
public final class PlayerState {
	private static final float DEFAULT_FLYING_SPEED = 0.05F;
	private static final float DEFAULT_WALKING_SPEED = 0.1F;
	private static final int DEFAULT_FOOD = 20;

	@Getter(AccessLevel.NONE)
	private final @NotNull LocalPlayer client;
	private final @NotNull AttributeMap attributes;
	private final @NotNull Effects effects = new Effects();
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

	/**
	 * Creates the state of a new player of the client's own player.
	 *
	 * @param version the client's Minecraft version
	 * @param client the client's own player, which keeps the entity, its game mode and its death
	 */
	public PlayerState(@NotNull MinecraftVersion version, @NotNull LocalPlayer client) {
		this.client = client;
		this.attributes = new AttributeMap(version, PlayerAttributes.supplier());
	}

	/**
	 * Follows a login, once the client followed it: a new player in the login's game mode.
	 */
	public void login() {
		attributes.reset();
		renew();
	}

	/**
	 * Follows a respawn, once the client followed it: a new player in the respawn's game mode.
	 */
	public void respawn() {
		attributes.keepBaseValues();
		renew();
	}

	/**
	 * Follows a game event once the client followed it, of which only a changed game mode concerns movement: it sets the
	 * abilities the client's new game mode implies.
	 *
	 * @param event the event
	 */
	public void gameEvent(@NotNull ClientboundGameEvent event) {
		if (event.getEvent() == GameEventType.CHANGE_GAME_MODE) abilitiesOf(client.gameMode());
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
	 * Takes the player's food.
	 *
	 * @param health the health and food
	 */
	public void health(@NotNull ClientboundSetHealth health) {
		food = health.getFood();
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
	 * Tells whether the player is a spectator, which flies through blocks ({@code Player.noPhysics}), as the client's
	 * game mode says.
	 *
	 * @return whether it spectates
	 */
	public boolean isSpectator() {
		return client.gameMode() == GameMode.SPECTATOR;
	}

	/**
	 * Tells whether the player is dead, as the client follows it, which holds no keys and reports no movement.
	 *
	 * @return whether it is dead
	 */
	public boolean isDead() {
		return client.isDead();
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

	private boolean own(@NotNull EntityId id) {
		return client.entityId().filter(id::equals).isPresent();
	}

	private void renew() {
		effects.clear();
		food = DEFAULT_FOOD;
		flyingSpeed = DEFAULT_FLYING_SPEED;
		walkingSpeed = DEFAULT_WALKING_SPEED;
		flying = false;
		abilitiesOf(client.gameMode());
	}

	/**
	 * Sets the abilities a game mode implies, as {@code GameType.updatePlayerAbilities} does.
	 */
	private void abilitiesOf(@NotNull GameMode mode) {
		mayFly = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
		if (mode == GameMode.SPECTATOR) flying = true;
		if (!mayFly) flying = false;
	}
}
