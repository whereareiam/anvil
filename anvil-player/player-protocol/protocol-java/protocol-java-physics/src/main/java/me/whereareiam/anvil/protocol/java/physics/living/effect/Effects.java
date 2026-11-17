package me.whereareiam.anvil.protocol.java.physics.living.effect;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The effects of a living entity as the client keeps them: each effect the server gives
 * ({@code ClientPacketListener.handleUpdateMobEffect}) with its amplifier, until the server takes it away
 * ({@code handleRemoveMobEffect}); the client counts an effect's duration down but never removes it itself.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
public final class Effects {
	private final @NotNull Map<MobEffect, Integer> amplifiers = new EnumMap<>(MobEffect.class);

	/**
	 * Tells whether the entity has an effect ({@code LivingEntity.hasEffect}).
	 *
	 * @param effect the effect
	 * @return whether it has it
	 */
	public boolean has(@NotNull MobEffect effect) {
		return amplifiers.containsKey(effect);
	}

	/**
	 * Returns the amplifier of an effect the entity has.
	 *
	 * @param effect the effect
	 * @return the amplifier, {@code 0} for level I, or empty when the entity does not have it
	 */
	public @NotNull OptionalInt amplifier(@NotNull MobEffect effect) {
		Integer amplifier = amplifiers.get(effect);
		return amplifier == null ? OptionalInt.empty() : OptionalInt.of(amplifier);
	}

	/**
	 * Gives the entity an effect or changes it.
	 *
	 * @param key the effect's key
	 * @param amplifier its amplifier
	 */
	public void add(@NotNull String key, int amplifier) {
		MobEffect.byKey(key).ifPresent(effect -> amplifiers.put(effect, amplifier));
	}

	/**
	 * Takes an effect away.
	 *
	 * @param key the effect's key
	 */
	public void remove(@NotNull String key) {
		MobEffect.byKey(key).ifPresent(amplifiers::remove);
	}

	/**
	 * Takes every effect away, as a new entity starts without any.
	 */
	public void clear() {
		amplifiers.clear();
	}
}
