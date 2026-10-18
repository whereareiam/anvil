package me.whereareiam.anvil.capability.world;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * A condition on a {@link SeenBlock} that describes itself, so a wait that times out says what it expected. Queries
 * combine with {@link #and}, {@link #or} and {@link #negate}; any other predicate combined with a query is described
 * as "a condition".
 *
 * <p>Queries by name, property or air need the block's name, which a release without block data does not give:
 * testing such a query on a block known only by its state ID throws {@link UnknownBlockNameException} instead of
 * answering false, so a wait fails at once with the reason rather than at its timeout. {@link #state(int)} and
 * {@link #any()} work on every release.</p>
 *
 * <pre>{@code
 * // an oak door's lower half, opened
 * BlockQuery open = BlockQuery.named("oak_door").and(BlockQuery.withProperty("half", "lower"))
 *         .and(BlockQuery.withProperty("open", "true"));
 * // any block that is not air
 * BlockQuery solid = BlockQuery.air().negate();
 * }</pre>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockQuery implements Predicate<SeenBlock> {
	private final @NotNull String description;
	private final @NotNull Predicate<SeenBlock> condition;
	private final boolean compound;

	/**
	 * Matches every known block.
	 *
	 * @return the query
	 */
	public static @NotNull BlockQuery any() {
		return matching("any block", block -> true);
	}

	/**
	 * Matches a block by its name. A name without a namespace is in {@code minecraft}.
	 *
	 * @param name the block's name, such as {@code stone} or {@code minecraft:oak_stairs}
	 * @return the query, which throws {@link UnknownBlockNameException} for a block known only by its state ID
	 */
	public static @NotNull BlockQuery named(@NotNull String name) {
		String namespaced = name.indexOf(':') < 0 ? "minecraft:" + name : name;
		return matching("named " + namespaced, block -> block.name().equals(namespaced));
	}

	/**
	 * Matches a block whose state has a property with a value, such as a door's {@code open} with {@code true}.
	 *
	 * @param name the property's name
	 * @param value the value, as vanilla writes it in a block state string
	 * @return the query, which throws {@link UnknownBlockNameException} for a block known only by its state ID
	 */
	public static @NotNull BlockQuery withProperty(@NotNull String name, @NotNull String value) {
		Optional<String> expected = Optional.of(value);
		return matching("with " + name + "=" + value, block -> block.property(name).equals(expected));
	}

	/**
	 * Matches air: {@code air}, {@code cave_air} and {@code void_air}.
	 *
	 * @return the query, which throws {@link UnknownBlockNameException} for a block known only by its state ID
	 */
	public static @NotNull BlockQuery air() {
		return matching("air", SeenBlock::isAir);
	}

	/**
	 * Matches a block by the global ID of its state in the player's release, which every release knows.
	 *
	 * @param stateId the global block state ID
	 * @return the query
	 */
	public static @NotNull BlockQuery state(int stateId) {
		return matching("in state " + stateId, block -> block.getStateId() == stateId);
	}

	/**
	 * Wraps any condition in a query with a description.
	 *
	 * @param description what the condition expects, such as "a lit furnace"
	 * @param condition the condition
	 * @return the query
	 */
	public static @NotNull BlockQuery matching(@NotNull String description, @NotNull Predicate<SeenBlock> condition) {
		return new BlockQuery(description, condition, false);
	}

	@Override
	public boolean test(@NotNull SeenBlock block) {
		return condition.test(block);
	}

	/**
	 * Matches a block that satisfies this query and another condition.
	 *
	 * @param other the other condition
	 * @return the combined query
	 */
	@Override
	public @NotNull BlockQuery and(@NotNull Predicate<? super SeenBlock> other) {
		return new BlockQuery(operand() + " and " + describe(other), block -> test(block) && other.test(block), true);
	}

	/**
	 * Matches a block that satisfies this query or another condition.
	 *
	 * @param other the other condition
	 * @return the combined query
	 */
	@Override
	public @NotNull BlockQuery or(@NotNull Predicate<? super SeenBlock> other) {
		return new BlockQuery(operand() + " or " + describe(other), block -> test(block) || other.test(block), true);
	}

	/**
	 * Matches a block that does not satisfy this query.
	 *
	 * @return the negated query
	 */
	@Override
	public @NotNull BlockQuery negate() {
		return new BlockQuery("not " + operand(), block -> !test(block), false);
	}

	/**
	 * Returns what the query expects, such as {@code named minecraft:oak_door and with open=true}.
	 *
	 * @return the description
	 */
	@Override
	public @NotNull String toString() {
		return description;
	}

	private String operand() {
		return compound ? "(" + description + ")" : description;
	}

	private static String describe(Predicate<?> condition) {
		return condition instanceof BlockQuery query ? query.operand() : "a condition";
	}
}
