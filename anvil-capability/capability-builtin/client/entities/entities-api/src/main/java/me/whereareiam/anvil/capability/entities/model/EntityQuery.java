package me.whereareiam.anvil.capability.entities.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * A condition on a {@link SeenEntity} that describes itself, so a wait that times out says what it expected.
 * Queries combine with {@link #and}, {@link #or} and {@link #negate}; any other predicate combined with a query is
 * described as "a condition".
 *
 * <pre>{@code
 * // a bubble with the text, close to the player or riding it
 * EntityQuery bubble = EntityQuery.labelled("hello").and(EntityQuery.aroundPlayer(4));
 * // an invisible armor stand, written as any predicate
 * EntityQuery stand = EntityQuery.ofType("armor_stand").and(SeenEntity::isInvisible);
 * }</pre>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityQuery implements Predicate<SeenEntity> {
	private final @NotNull String description;
	private final @NotNull Predicate<SeenEntity> condition;
	private final boolean compound;

	/**
	 * Matches every entity.
	 *
	 * @return the query
	 */
	public static @NotNull EntityQuery any() {
		return matching("any entity", entity -> true);
	}

	/**
	 * Matches an entity whose custom name or display text contains a text.
	 *
	 * @param text the text to look for
	 * @return the query
	 */
	public static @NotNull EntityQuery labelled(@NotNull String text) {
		return matching("labelled '" + text + "'", entity -> entity.labelContains(text));
	}

	/**
	 * Matches an entity of a type. A type without a namespace is in {@code minecraft}.
	 *
	 * @param type the type, such as {@code text_display} or {@code minecraft:armor_stand}
	 * @return the query
	 */
	public static @NotNull EntityQuery ofType(@NotNull String type) {
		String namespaced = type.indexOf(':') < 0 ? "minecraft:" + type : type;
		return matching("of type " + namespaced, entity -> entity.getType().equals(namespaced));
	}

	/**
	 * Matches an entity at most a distance away from the player.
	 *
	 * @param distance the largest distance in blocks
	 * @return the query
	 */
	public static @NotNull EntityQuery nearPlayer(double distance) {
		return matching("within " + distance + " blocks of the player", entity -> {
			Double playerDistance = entity.getPlayerDistance();
			return playerDistance != null && playerDistance <= distance;
		});
	}

	/**
	 * Matches an entity that rides the player, directly or on top of another one that does.
	 *
	 * @return the query
	 */
	public static @NotNull EntityQuery ridingPlayer() {
		return matching("riding the player", SeenEntity::isRidingPlayer);
	}

	/**
	 * Matches an entity close to the player or riding it, as text a plugin shows above a player usually is: the
	 * server sends no position for a passenger, so its distance alone says little.
	 *
	 * @param distance the largest distance in blocks
	 * @return the query
	 */
	public static @NotNull EntityQuery aroundPlayer(double distance) {
		return matching("within " + distance + " blocks of or riding the player", entity -> {
			Double playerDistance = entity.getPlayerDistance();
			return entity.isRidingPlayer() || playerDistance != null && playerDistance <= distance;
		});
	}

	/**
	 * Wraps any condition in a query with a description.
	 *
	 * @param description what the condition expects, such as "a glowing zombie"
	 * @param condition the condition
	 * @return the query
	 */
	public static @NotNull EntityQuery matching(@NotNull String description, @NotNull Predicate<SeenEntity> condition) {
		return new EntityQuery(description, condition, false);
	}

	@Override
	public boolean test(@NotNull SeenEntity entity) {
		return condition.test(entity);
	}

	/**
	 * Matches an entity that satisfies this query and another condition.
	 *
	 * @param other the other condition
	 * @return the combined query
	 */
	@Override
	public @NotNull EntityQuery and(@NotNull Predicate<? super SeenEntity> other) {
		return new EntityQuery(operand() + " and " + describe(other), entity -> test(entity) && other.test(entity), true);
	}

	/**
	 * Matches an entity that satisfies this query or another condition.
	 *
	 * @param other the other condition
	 * @return the combined query
	 */
	@Override
	public @NotNull EntityQuery or(@NotNull Predicate<? super SeenEntity> other) {
		return new EntityQuery(operand() + " or " + describe(other), entity -> test(entity) || other.test(entity), true);
	}

	/**
	 * Matches an entity that does not satisfy this query.
	 *
	 * @return the negated query
	 */
	@Override
	public @NotNull EntityQuery negate() {
		return new EntityQuery("not " + operand(), entity -> !test(entity), false);
	}

	/**
	 * Returns what the query expects, such as {@code labelled 'hello' and (riding the player)}.
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
		return condition instanceof EntityQuery query ? query.operand() : "a condition";
	}
}
