package me.whereareiam.anvil.capability.entities.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * An entity as the player knew it at one moment. The value never changes; a wait or {@link
 * me.whereareiam.anvil.capability.entities.Entities#all()} returns a new one for a later state.
 */
@Value
@Builder(toBuilder = true)
public class SeenEntity {
	/**
	 * Number of the player's connection the entity belongs to, starting at 1. Entity IDs are reused across
	 * connections, so the connection and the ID together name one entity.
	 */
	int connection;
	/**
	 * Entity ID the server gave the entity, which {@code Interaction} uses to interact with it.
	 */
	int id;
	/**
	 * Unique ID the server sent with the spawn, or null for an entity spawned without one, such as an experience orb
	 * before Minecraft 1.21.2.
	 */
	@Nullable UUID uniqueId;
	/**
	 * Entity type with its namespace, such as {@code minecraft:armor_stand} or {@code minecraft:text_display}.
	 */
	@NotNull String type;
	double x;
	double y;
	double z;
	/**
	 * Direction the entity faces horizontally, in degrees.
	 */
	float yaw;
	/**
	 * Direction the entity faces vertically, in degrees.
	 */
	float pitch;
	/**
	 * Custom name flattened to plain text, or null when the entity has none.
	 */
	@Nullable String customName;
	/**
	 * Whether the client shows the custom name above the entity.
	 */
	boolean customNameVisible;
	/**
	 * Whether the entity is invisible, such as the invisible armor stand that carries a floating name.
	 */
	boolean invisible;
	/**
	 * Whether the entity glows, outlined through walls.
	 */
	boolean glowing;
	/**
	 * Whether the entity is on fire.
	 */
	boolean onFire;
	/**
	 * Whether the entity, usually a player, sneaks.
	 */
	boolean sneaking;
	boolean sprinting;
	boolean swimming;
	/**
	 * Whether the entity glides with elytra.
	 */
	boolean gliding;
	/**
	 * Text of a text display flattened to plain text, or null for any other entity and before the server sent it.
	 */
	@Nullable String text;
	/**
	 * The item the entity shows, such as a dropped item, the item in an item frame or an item display, a thrown item
	 * or a firework, or null for an entity that shows none.
	 */
	@Nullable EntityItem item;
	/**
	 * Entity ID of the entity this one rides, which is the player's own entity ID when it rides the player, or null.
	 */
	@Nullable Integer vehicleId;
	/**
	 * Entity IDs of the entities riding this one.
	 */
	@NotNull List<Integer> passengerIds;
	/**
	 * Whether the entity rides the player, directly or on top of another entity that does.
	 */
	boolean ridingPlayer;
	/**
	 * Distance from the player's position to the entity's, both as the client knew them when this value was taken,
	 * or null before the server placed the player.
	 */
	@Nullable Double playerDistance;

	/**
	 * Returns what a player reads on the entity: its custom name, or else the text of a text display.
	 *
	 * @return the label, or null when the entity has neither
	 */
	public @Nullable String label() {
		return customName != null ? customName : text;
	}

	/**
	 * Tells whether the custom name or the display text contains a text.
	 *
	 * @param text the text to look for
	 * @return true when either contains it
	 */
	public boolean labelContains(@NotNull String text) {
		return customName != null && customName.contains(text) || this.text != null && this.text.contains(text);
	}

	/**
	 * Tells whether another value describes the same entity, possibly in another state.
	 *
	 * @param other another value
	 * @return true when both have the same connection, ID and unique ID
	 */
	public boolean sameEntity(@NotNull SeenEntity other) {
		return connection == other.connection && id == other.id
				&& (uniqueId == null ? other.uniqueId == null : uniqueId.equals(other.uniqueId));
	}
}
