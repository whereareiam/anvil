package me.whereareiam.anvil.capability.movement.java.physics.server;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeModifier;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The attributes of the player's own entity as its client keeps them ({@code AttributeMap}): each attribute's base and
 * modifiers as the server last sent them ({@code ClientPacketListener.handleUpdateAttributes} replaces both), plus the
 * sprinting modifier the client adds and removes itself ({@code LivingEntity.setSprinting}). A value is computed as
 * {@code AttributeInstance.calculateValue} does: the additions, then the multiples of the base, then the multiples of
 * the total, each in the order the modifiers came, clamped to the attribute's range. Until the server sends an
 * attribute, it has the player's vanilla default.
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
public final class Attributes {
	private static final AttributeModifier SPRINTING_SINCE_1_21 = new AttributeModifier("minecraft:sprinting", 0.3F,
			AttributeOperation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier SPRINTING_BEFORE_1_21 = new AttributeModifier(
			"662a6b8d-da3e-4c1c-8813-96ea6097278d", 0.3F, AttributeOperation.ADD_MULTIPLIED_TOTAL);

	private final @NotNull MinecraftVersion version;
	private final @NotNull AttributeModifier sprinting;
	private final @NotNull Map<PlayerAttribute, Instance> instances = new EnumMap<>(PlayerAttribute.class);

	/**
	 * Creates the default attributes of a client.
	 *
	 * @param version the client's Minecraft version
	 */
	public Attributes(@NotNull MinecraftVersion version) {
		this.version = version;
		this.sprinting = ClientBehaviour.NAMESPACED_MODIFIERS.appliesTo(version)
				? SPRINTING_SINCE_1_21
				: SPRINTING_BEFORE_1_21;
		reset();
	}

	/**
	 * Returns an attribute's value.
	 *
	 * @param attribute the attribute
	 * @return the value the client uses
	 */
	public double value(@NotNull PlayerAttribute attribute) {
		return instances.get(attribute).value(attribute);
	}

	/**
	 * Takes the attributes the server sent for the player's own entity, each replacing what the client had of it;
	 * attributes the client's version does not have, or movement does not read, are left out.
	 *
	 * @param snapshots the attributes as the server sent them
	 */
	public void update(@NotNull List<AttributeSnapshot> snapshots) {
		for (AttributeSnapshot snapshot : snapshots)
			PlayerAttribute.byKey(snapshot.getAttribute())
					.filter(attribute -> attribute.existsIn(version))
					.ifPresent(attribute -> instances.put(attribute, Instance.of(snapshot)));
	}

	/**
	 * Adds or removes the sprinting modifier of the movement speed, as {@code LivingEntity.setSprinting} does.
	 *
	 * @param sprinting whether the player sprints
	 */
	public void sprinting(boolean sprinting) {
		Instance speed = instances.get(PlayerAttribute.MOVEMENT_SPEED);
		speed.modifiers.remove(this.sprinting.getId());
		if (sprinting) speed.modifiers.put(this.sprinting.getId(), this.sprinting);
	}

	/**
	 * Keeps only the base values, as a respawned player takes them over ({@code AttributeMap.assignBaseValues}).
	 */
	public void keepBaseValues() {
		instances.values().forEach(instance -> instance.modifiers.clear());
	}

	/**
	 * Returns every attribute to the player's vanilla default, as a new player starts with.
	 */
	public void reset() {
		for (PlayerAttribute attribute : PlayerAttribute.values())
			instances.put(attribute, new Instance(attribute.getDefaultValue(), new LinkedHashMap<>()));
	}

	/**
	 * One attribute's base and modifiers by ID, in the order they were added ({@code AttributeInstance}).
	 */
	private record Instance(double base, @NotNull Map<String, AttributeModifier> modifiers) {
		private static @NotNull Instance of(@NotNull AttributeSnapshot snapshot) {
			Map<String, AttributeModifier> modifiers = new LinkedHashMap<>();
			for (AttributeModifier modifier : snapshot.getModifiers()) modifiers.put(modifier.getId(), modifier);

			return new Instance(snapshot.getBase(), modifiers);
		}

		private double value(@NotNull PlayerAttribute attribute) {
			double added = base;
			for (AttributeModifier modifier : of(AttributeOperation.ADD_VALUE)) added += modifier.getAmount();

			double value = added;
			for (AttributeModifier modifier : of(AttributeOperation.ADD_MULTIPLIED_BASE))
				value += added * modifier.getAmount();
			for (AttributeModifier modifier : of(AttributeOperation.ADD_MULTIPLIED_TOTAL))
				value *= 1.0 + modifier.getAmount();

			return attribute.sanitize(value);
		}

		private @NotNull List<AttributeModifier> of(@NotNull AttributeOperation operation) {
			return modifiers.values().stream().filter(modifier -> modifier.getOperation() == operation).toList();
		}
	}
}
