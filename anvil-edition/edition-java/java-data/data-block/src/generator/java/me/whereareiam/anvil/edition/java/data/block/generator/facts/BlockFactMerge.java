package me.whereareiam.anvil.edition.java.data.block.generator.facts;

import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Merges the {@code block-facts.toml} entries that name one block: behaviours add up, every other value is set by at
 * most one entry, and what none sets keeps vanilla's default from {@link BlockFact#DEFAULT}.
 */
final class BlockFactMerge {
	private final @NotNull String name;
	private final @NotNull Set<MovementBehaviour> behaviours = EnumSet.noneOf(MovementBehaviour.class);
	private final @NotNull Map<String, Integer> propertyStarts = new HashMap<>();
	private @Nullable BlockKind kind;
	private @Nullable CollisionKind collision;
	private @Nullable FluidType fluid;
	private @Nullable Float friction;
	private @Nullable Float speedFactor;
	private @Nullable Float jumpFactor;
	private @Nullable Double bounce;
	private @Nullable StuckSpeed stuck;

	BlockFactMerge(@NotNull String name) {
		this.name = name;
	}

	/**
	 * Adds one entry naming the block.
	 *
	 * @throws IllegalStateException when the entry sets a value another entry set, or names a default
	 */
	void add(@NotNull BlockFactEntry entry) {
		kind = once("kind", kind, notDefault("kind", entry.kind(), BlockKind.OTHER));
		collision = once("collision", collision, notDefault("collision", entry.collision(), CollisionKind.FIXED));
		fluid = once("fluid", fluid, notDefault("fluid", entry.fluid(), FluidType.NONE));
		friction = once("friction", friction, entry.friction() == null ? null : entry.friction().asFloat("friction"));
		speedFactor = once("speed-factor", speedFactor,
				entry.speedFactor() == null ? null : entry.speedFactor().asFloat("speed-factor"));
		jumpFactor = once("jump-factor", jumpFactor,
				entry.jumpFactor() == null ? null : entry.jumpFactor().asFloat("jump-factor"));
		bounce = once("bounce", bounce, entry.bounce() == null ? null : entry.bounce().asDouble());
		BlockFactEntry.Stuck slowdown = entry.stuck();
		stuck = once("stuck", stuck, slowdown == null ? null
				: new StuckSpeed(slowdown.horizontal().asDouble(), slowdown.vertical().asDouble()));
		behaviours.addAll(entry.behaviours());
		entry.propertyStarts().forEach((property, start) -> {
			if (propertyStarts.putIfAbsent(property, start) != null)
				throw new IllegalStateException(
						"block-facts.toml sets the start of " + name + "'s " + property + " more than once");
		});
	}

	/**
	 * The block's merged facts.
	 */
	@NotNull BlockFact fact() {
		BlockFact defaults = BlockFact.DEFAULT;
		BlockMovement movement = BlockMovement.builder()
				.friction(friction == null ? defaults.movement().friction() : friction)
				.speedFactor(speedFactor == null ? defaults.movement().speedFactor() : speedFactor)
				.jumpFactor(jumpFactor == null ? defaults.movement().jumpFactor() : jumpFactor)
				.bounceFactor(bounce == null ? defaults.movement().bounceFactor() : bounce)
				.stuckSpeed(stuck)
				.behaviours(behaviours)
				.build();

		return BlockFact.builder()
				.kind(kind == null ? defaults.kind() : kind)
				.collision(collision == null ? defaults.collision() : collision)
				.fluid(fluid == null ? defaults.fluid() : fluid)
				.movement(movement)
				.propertyStarts(Map.copyOf(propertyStarts))
				.build();
	}

	private <T> @Nullable T once(String field, @Nullable T current, @Nullable T value) {
		if (current != null && value != null)
			throw new IllegalStateException("block-facts.toml sets " + field + " of " + name + " more than once");

		return current == null ? value : current;
	}

	private <T> @Nullable T notDefault(String field, @Nullable T value, T fallback) {
		if (fallback.equals(value))
			throw new IllegalStateException("block-facts.toml names " + field + " " + value + " for " + name
					+ ", which every block it does not name has; leave it out");

		return value;
	}
}
