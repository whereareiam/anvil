package me.whereareiam.anvil.edition.java.physics.living.attribute;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.type.entity.AttributeOperation;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The attributes of a living entity as its client keeps them ({@code AttributeMap}): each attribute's base and
 * modifiers as the server last sent them ({@code ClientPacketListener.handleUpdateAttributes} replaces both), plus the
 * sprinting modifier the client adds and removes itself ({@code LivingEntity.setSprinting}). A value is computed as
 * {@code AttributeInstance.calculateValue} does: the additions, then the multiples of the base, then the multiples of
 * the total, each in the order the modifiers came, clamped to the attribute's range. Until the server sends an
 * attribute, it has the base its kind of entity gives it ({@link AttributeSupplier}).
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
public final class AttributeMap {
	private static final AttributeModifier SPRINTING_SINCE_1_21 = new AttributeModifier("minecraft:sprinting", 0.3F,
			AttributeOperation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier SPRINTING_BEFORE_1_21 = new AttributeModifier(
			"662a6b8d-da3e-4c1c-8813-96ea6097278d", 0.3F, AttributeOperation.ADD_MULTIPLIED_TOTAL);

	private final @NotNull MinecraftVersion version;
	private final @NotNull AttributeSupplier supplier;
	private final @NotNull AttributeModifier sprinting;
	private final @NotNull Map<Attribute, Instance> instances = new HashMap<>();

	/**
	 * Creates the attributes of a new entity of a kind.
	 *
	 * @param version the client's Minecraft version
	 * @param supplier the attributes the kind of entity has and their bases
	 */
	public AttributeMap(@NotNull MinecraftVersion version, @NotNull AttributeSupplier supplier) {
		this.version = version;
		this.supplier = supplier;
		this.sprinting = EntityBehaviour.NAMESPACED_MODIFIERS.appliesTo(version)
				? SPRINTING_SINCE_1_21
				: SPRINTING_BEFORE_1_21;
		reset();
	}

	/**
	 * Returns an attribute's value.
	 *
	 * @param attribute the attribute
	 * @return the value the client uses
	 * @throws IllegalArgumentException when the entity's kind has no such attribute
	 */
	public double value(@NotNull Attribute attribute) {
		Instance instance = instances.get(attribute);
		if (instance == null) throw new IllegalArgumentException("The entity has no attribute " + attribute);

		return instance.value(attribute);
	}

	/**
	 * Takes the attributes the server sent for the entity, each replacing what the client had of it; attributes the
	 * client's version does not have, or the entity's kind does not, are left out.
	 *
	 * @param snapshots the attributes as the server sent them
	 */
	public void update(@NotNull List<AttributeSnapshot> snapshots) {
		for (AttributeSnapshot snapshot : snapshots)
			supplier.byKey(snapshot.attribute())
					.filter(attribute -> attribute.existsIn(version))
					.ifPresent(attribute -> instances.put(attribute, Instance.of(snapshot)));
	}

	/**
	 * Adds or removes the sprinting modifier of the movement speed, as {@code LivingEntity.setSprinting} does.
	 *
	 * @param sprinting whether the entity sprints
	 */
	public void sprinting(boolean sprinting) {
		Instance speed = instances.get(LivingAttributes.MOVEMENT_SPEED);
		speed.modifiers.remove(this.sprinting.id());
		if (sprinting) speed.modifiers.put(this.sprinting.id(), this.sprinting);
	}

	/**
	 * Keeps only the base values, as a respawned player takes them over ({@code AttributeMap.assignBaseValues}).
	 */
	public void keepBaseValues() {
		instances.values().forEach(instance -> instance.modifiers.clear());
	}

	/**
	 * Returns every attribute to the base its kind of entity gives it, as a new entity starts with.
	 */
	public void reset() {
		supplier.bases().forEach((attribute, base) -> instances.put(attribute, new Instance(base, new LinkedHashMap<>())));
	}

	/**
	 * One attribute's base and modifiers by ID, in the order they were added ({@code AttributeInstance}).
	 */
	private record Instance(double base, @NotNull Map<String, AttributeModifier> modifiers) {
		private static @NotNull Instance of(@NotNull AttributeSnapshot snapshot) {
			Map<String, AttributeModifier> modifiers = new LinkedHashMap<>();
			for (AttributeModifier modifier : snapshot.modifiers()) modifiers.put(modifier.id(), modifier);

			return new Instance(snapshot.base(), modifiers);
		}

		private double value(@NotNull Attribute attribute) {
			double added = base;
			for (AttributeModifier modifier : of(AttributeOperation.ADD_VALUE)) added += modifier.amount();

			double value = added;
			for (AttributeModifier modifier : of(AttributeOperation.ADD_MULTIPLIED_BASE))
				value += added * modifier.amount();
			for (AttributeModifier modifier : of(AttributeOperation.ADD_MULTIPLIED_TOTAL))
				value *= 1.0 + modifier.amount();

			return attribute.sanitize(value);
		}

		private @NotNull List<AttributeModifier> of(@NotNull AttributeOperation operation) {
			return modifiers.values().stream().filter(modifier -> modifier.operation() == operation).toList();
		}
	}
}
