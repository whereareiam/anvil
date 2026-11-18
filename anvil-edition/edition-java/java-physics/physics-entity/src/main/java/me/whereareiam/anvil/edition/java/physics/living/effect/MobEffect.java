package me.whereareiam.anvil.edition.java.physics.living.effect;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * The effects the client's physics reads itself, as vanilla's {@code MobEffects} names them. Other effects that change
 * movement, such as speed and slowness, change the entity's attributes on the server ({@code onEffectAdded} adds
 * their modifiers only there), which the client follows through the attributes the server sends.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum MobEffect {
	/**
	 * Raises the jump power by {@code 0.1F} per level ({@code LivingEntity.getJumpBoostPower}).
	 */
	JUMP_BOOST("minecraft:jump_boost"),
	/**
	 * Lifts the entity towards {@code 0.05} blocks a tick per level instead of gravity ({@code travelInAir}).
	 */
	LEVITATION("minecraft:levitation"),
	/**
	 * Limits gravity to {@code 0.01} while the entity falls ({@code getEffectiveGravity}).
	 */
	SLOW_FALLING("minecraft:slow_falling"),
	/**
	 * Lets the entity keep {@code 0.96} of its speed in water ({@code travelInWater}).
	 */
	DOLPHINS_GRACE("minecraft:dolphins_grace"),
	/**
	 * Keeps a player from starting to sprint and stops its sprint ({@code isMobilityRestricted}).
	 */
	BLINDNESS("minecraft:blindness");

	private final @NotNull String key;

	/**
	 * Looks an effect up by the key the protocol reports.
	 *
	 * @param key the effect's key, such as {@code minecraft:levitation}
	 * @return the effect, or empty for one the physics does not read
	 */
	public static @NotNull Optional<MobEffect> byKey(@NotNull String key) {
		return Arrays.stream(values()).filter(effect -> effect.key.equals(key)).findFirst();
	}
}
