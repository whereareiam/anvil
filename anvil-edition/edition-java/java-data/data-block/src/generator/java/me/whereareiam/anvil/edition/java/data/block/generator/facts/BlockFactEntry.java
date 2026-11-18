package me.whereareiam.anvil.edition.java.data.block.generator.facts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One {@code [[block]]} entry of {@code block-facts.toml}: the blocks it names, the vanilla code its values come from,
 * and the values it sets. An absent value leaves vanilla's default to this entry; another entry may set it.
 */
@Value
@Builder
@Jacksonized
public class BlockFactEntry {
	/**
	 * The namespaced blocks the entry is about, such as {@code minecraft:ice}.
	 */
	@Singular
	@NotNull List<String> names;
	/**
	 * The vanilla code its values come from.
	 */
	@NotNull String source;
	/**
	 * What the blocks are where vanilla's movement code asks.
	 */
	@Nullable BlockKind kind;
	/**
	 * How vanilla decides their collision shape.
	 */
	@Nullable CollisionKind collision;
	/**
	 * The fluid every state of the blocks holds.
	 */
	@Nullable FluidType fluid;
	/**
	 * Slipperiness, a float in vanilla.
	 */
	@Nullable VanillaNumber friction;
	/**
	 * The speed factor, a float in vanilla.
	 */
	@JsonProperty("speed-factor")
	@Nullable VanillaNumber speedFactor;
	/**
	 * The jump factor, a float in vanilla.
	 */
	@JsonProperty("jump-factor")
	@Nullable VanillaNumber jumpFactor;
	/**
	 * The factor of the reversed fall speed of an entity landing on the blocks.
	 */
	@Nullable VanillaNumber bounce;
	/**
	 * How the blocks slow an entity inside them.
	 */
	@Nullable Stuck stuck;
	/**
	 * The blocks' movement behaviours.
	 */
	@Singular
	@NotNull Set<MovementBehaviour> behaviours;
	/**
	 * The lowest value of each integer property that does not start at {@code 0}.
	 */
	@JsonProperty("property-start")
	@Singular
	@NotNull Map<String, Integer> propertyStarts;

	/**
	 * The factors of {@code Entity.makeStuckInBlock}.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class Stuck {
		/**
		 * The factor of the horizontal movement.
		 */
		@NotNull VanillaNumber horizontal;
		/**
		 * The factor of the vertical movement.
		 */
		@NotNull VanillaNumber vertical;
	}
}
