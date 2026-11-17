package me.whereareiam.anvil.protocol.java.physics.living.attribute;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The attributes of every living entity that its physics reads, as vanilla's {@code Attributes} registers them and
 * {@code LivingEntity.createLivingAttributes} gives them to each living entity.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LivingAttributes {
	/**
	 * {@code movement_speed}: {@code LivingEntity.getSpeed} where the entity's controls read it, {@code 0.7}.
	 */
	public static final @NotNull Attribute MOVEMENT_SPEED =
			Attribute.of("minecraft:movement_speed", null, 0.7, 0.0, 1024.0);
	/**
	 * {@code jump_strength}: the jump power, {@code 0.42F}.
	 */
	public static final @NotNull Attribute JUMP_STRENGTH =
			Attribute.of("minecraft:jump_strength", "1.20.5", 0.42F, 0.0, 32.0);
	/**
	 * {@code gravity}: what falling adds to the downward speed each tick, {@code 0.08}.
	 */
	public static final @NotNull Attribute GRAVITY =
			Attribute.of("minecraft:gravity", "1.20.5", 0.08, -1.0, 1.0);
	/**
	 * {@code step_height}: how high the entity steps up onto a block, {@code 0.6}.
	 */
	public static final @NotNull Attribute STEP_HEIGHT =
			Attribute.of("minecraft:step_height", "1.20.5", 0.6, 0.0, 10.0);
	/**
	 * {@code scale}: the factor of the entity's size, {@code 1.0}.
	 */
	public static final @NotNull Attribute SCALE =
			Attribute.of("minecraft:scale", "1.20.5", 1.0, 0.0625, 16.0);
	/**
	 * {@code safe_fall_distance}: how far the entity falls without damage, which only the server reads.
	 */
	public static final @NotNull Attribute SAFE_FALL_DISTANCE =
			Attribute.of("minecraft:safe_fall_distance", "1.20.5", 3.0, -1024.0, 1024.0);
	/**
	 * {@code movement_efficiency}: how little slow blocks slow the entity ({@code LivingEntity.getBlockSpeedFactor}).
	 */
	public static final @NotNull Attribute MOVEMENT_EFFICIENCY =
			Attribute.of("minecraft:movement_efficiency", "1.21", 0.0, 0.0, 1.0);
	/**
	 * {@code water_movement_efficiency}: how little water slows the entity ({@code travelInWater}).
	 */
	public static final @NotNull Attribute WATER_MOVEMENT_EFFICIENCY =
			Attribute.of("minecraft:water_movement_efficiency", "1.21", 0.0, 0.0, 1.0);

	/**
	 * Returns every living attribute, in the order vanilla registers them.
	 *
	 * @return the attributes
	 */
	public static @NotNull List<Attribute> all() {
		return List.of(MOVEMENT_SPEED, JUMP_STRENGTH, GRAVITY, STEP_HEIGHT, SCALE, SAFE_FALL_DISTANCE, MOVEMENT_EFFICIENCY,
				WATER_MOVEMENT_EFFICIENCY);
	}
}
