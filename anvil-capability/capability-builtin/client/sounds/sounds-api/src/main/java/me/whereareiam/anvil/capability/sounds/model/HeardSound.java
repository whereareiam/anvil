package me.whereareiam.anvil.capability.sounds.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A sound the server told the player to play. It plays either at a position or following an entity, so exactly one
 * of {@link #getPosition()} and {@link #getEntityId()} is set.
 */
@Value
@Builder
public class HeardSound {
	private static final String DEFAULT_NAMESPACE = "minecraft:";

	/**
	 * The sound's namespaced key, such as {@code minecraft:entity.player.levelup}, whether the server named it or
	 * referred to it by its registry ID. A registry ID the player's release does not know is kept as its decimal
	 * number.
	 */
	@NotNull String key;
	/**
	 * The volume slider the sound belongs to.
	 */
	@NotNull SoundCategory category;
	/**
	 * The sound's volume, where {@code 1} is full volume and higher values carry further.
	 */
	float volume;
	/**
	 * The sound's pitch, from {@code 0.5} to {@code 2}, where {@code 1} is unchanged.
	 */
	float pitch;
	/**
	 * Where the sound plays, or null for a sound that follows an entity.
	 */
	@Nullable SoundPosition position;
	/**
	 * The protocol ID of the entity the sound follows, or null for a sound at a position.
	 */
	@Nullable Integer entityId;

	/**
	 * Tells whether the sound has the supplied key; a key without a namespace means the {@code minecraft} namespace.
	 *
	 * <pre>{@code
	 * sound.is("entity.player.levelup");           // minecraft:entity.player.levelup
	 * sound.is("myplugin:alert");
	 * }</pre>
	 *
	 * @param key sound key, with or without its namespace
	 * @return whether the sound has that key
	 */
	public boolean is(@NotNull String key) {
		return this.key.equals(key.indexOf(':') < 0 ? DEFAULT_NAMESPACE + key : key);
	}
}
