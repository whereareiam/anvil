package me.whereareiam.anvil.capability.movement.java.physics.server;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * The attributes of the player that its client's physics reads, as vanilla's {@code Attributes} registers them: each
 * with its key in the names of Minecraft 1.21.2, the release that made it an attribute, the player's default and the
 * range {@code RangedAttribute.sanitizeValue} clamps it to. Before a release has an attribute, its client uses the
 * same number as a constant, which is the default here.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum PlayerAttribute {
	/**
	 * {@code movement_speed}: {@code Player.getSpeed}, {@code 0.1F} for a player ({@code Player.createAttributes}).
	 */
	MOVEMENT_SPEED("minecraft:movement_speed", null, 0.1F, 0.0, 1024.0),
	/**
	 * {@code jump_strength}: the jump power, {@code 0.42F}.
	 */
	JUMP_STRENGTH("minecraft:jump_strength", "1.20.5", 0.42F, 0.0, 32.0),
	/**
	 * {@code gravity}: what falling adds to the downward speed each tick, {@code 0.08}.
	 */
	GRAVITY("minecraft:gravity", "1.20.5", 0.08, -1.0, 1.0),
	/**
	 * {@code step_height}: how high the player steps up onto a block, {@code 0.6}.
	 */
	STEP_HEIGHT("minecraft:step_height", "1.20.5", 0.6, 0.0, 10.0),
	/**
	 * {@code scale}: the factor of the player's size, {@code 1.0}.
	 */
	SCALE("minecraft:scale", "1.20.5", 1.0, 0.0625, 16.0),
	/**
	 * {@code safe_fall_distance}: how far the player falls without damage, which only the server reads.
	 */
	SAFE_FALL_DISTANCE("minecraft:safe_fall_distance", "1.20.5", 3.0, -1024.0, 1024.0),
	/**
	 * {@code sneaking_speed}: the factor of the input while the player crouches, {@code 0.3}.
	 */
	SNEAKING_SPEED("minecraft:sneaking_speed", "1.21", 0.3, 0.0, 1.0),
	/**
	 * {@code movement_efficiency}: how little slow blocks slow the player ({@code LivingEntity.getBlockSpeedFactor}).
	 */
	MOVEMENT_EFFICIENCY("minecraft:movement_efficiency", "1.21", 0.0, 0.0, 1.0),
	/**
	 * {@code water_movement_efficiency}: how little water slows the player ({@code travelInWater}).
	 */
	WATER_MOVEMENT_EFFICIENCY("minecraft:water_movement_efficiency", "1.21", 0.0, 0.0, 1.0),
	/**
	 * {@code submerged_mining_speed}: how fast the player mines under water, which movement does not read.
	 */
	SUBMERGED_MINING_SPEED("minecraft:submerged_mining_speed", "1.21", 0.2, 0.0, 20.0);

	private final @NotNull String key;
	@Getter(AccessLevel.NONE)
	private final @Nullable String since;
	private final double defaultValue;
	private final double min;
	private final double max;

	/**
	 * Looks an attribute up by the key the protocol reports.
	 *
	 * @param key the attribute's key, such as {@code minecraft:gravity}
	 * @return the attribute, or empty for one movement does not read
	 */
	public static @NotNull Optional<PlayerAttribute> byKey(@NotNull String key) {
		return Arrays.stream(values()).filter(attribute -> attribute.key.equals(key)).findFirst();
	}

	/**
	 * Tells whether a client of a version has this attribute.
	 *
	 * @param version the client's Minecraft version
	 * @return whether the server can send it
	 */
	public boolean existsIn(@NotNull MinecraftVersion version) {
		return since == null || version.isAtLeast(MinecraftVersion.parse(since));
	}

	/**
	 * Clamps a value to the attribute's range, as {@code RangedAttribute.sanitizeValue} does.
	 *
	 * @param value the computed value
	 * @return the value the game uses
	 */
	public double sanitize(double value) {
		return Double.isNaN(value) ? min : Math.clamp(value, min, max);
	}
}
