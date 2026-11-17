package me.whereareiam.anvil.protocol.java.physics.living.attribute;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The attributes a kind of living entity has and their base values, as vanilla's {@code AttributeSupplier} gives them
 * to a new entity: {@link #living()} is {@code LivingEntity.createLivingAttributes}, and a kind of entity adds its own
 * attributes or other bases, as {@code Player.createAttributes} does.
 *
 * <pre>{@code
 * AttributeSupplier player = AttributeSupplier.living()
 *         .with(LivingAttributes.MOVEMENT_SPEED, 0.1F)
 *         .with(sneakingSpeed);
 * }</pre>
 */
public final class AttributeSupplier {
	private final @NotNull Map<Attribute, Double> bases;

	private AttributeSupplier(@NotNull Map<Attribute, Double> bases) {
		this.bases = Collections.unmodifiableMap(bases);
	}

	/**
	 * Returns the attributes of every living entity at their defaults.
	 *
	 * @return the supplier
	 */
	public static @NotNull AttributeSupplier living() {
		Map<Attribute, Double> bases = new LinkedHashMap<>();
		for (Attribute attribute : LivingAttributes.all()) bases.put(attribute, attribute.getDefaultValue());

		return new AttributeSupplier(bases);
	}

	/**
	 * Adds an attribute at its default, or keeps one the supplier has.
	 *
	 * @param attribute the attribute
	 * @return a supplier with it
	 */
	public @NotNull AttributeSupplier with(@NotNull Attribute attribute) {
		return bases.containsKey(attribute) ? this : with(attribute, attribute.getDefaultValue());
	}

	/**
	 * Adds an attribute or changes its base.
	 *
	 * @param attribute the attribute
	 * @param base its base value for this kind of entity
	 * @return a supplier with it
	 */
	public @NotNull AttributeSupplier with(@NotNull Attribute attribute, double base) {
		Map<Attribute, Double> changed = new LinkedHashMap<>(bases);
		changed.put(attribute, base);
		return new AttributeSupplier(changed);
	}

	/**
	 * Returns the attributes and their bases, in the order they were added.
	 *
	 * @return the bases by attribute, unmodifiable
	 */
	public @NotNull Map<Attribute, Double> bases() {
		return bases;
	}

	/**
	 * Looks an attribute of this kind of entity up by the key the protocol reports.
	 *
	 * @param key the attribute's key, such as {@code minecraft:gravity}
	 * @return the attribute, or empty for one this kind of entity does not have
	 */
	public @NotNull Optional<Attribute> byKey(@NotNull String key) {
		return bases.keySet().stream().filter(attribute -> attribute.getKey().equals(key)).findFirst();
	}
}
