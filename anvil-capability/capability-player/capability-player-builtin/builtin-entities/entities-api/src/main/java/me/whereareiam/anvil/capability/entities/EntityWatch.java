package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the changes of the entities a player sees from the moment the watch was created.
 *
 * <p>An entity appears for a watch when, after the watch's position, a change makes it satisfy a condition it did
 * not satisfy before: it spawns already satisfying it, or a later change, such as the custom name the server sends
 * after the spawn, makes it satisfy it. An entity that satisfied the condition before the watch was created does not
 * appear by staying the same. Each {@link #appeared} wait continues after the change the previous one found, so
 * entities that look alike are found one per wait.</p>
 *
 * <p>A watch follows facts: spawns, removals, and changes of what an entity shows or rides. Each carries the entity
 * as it was at that moment, where it was included, but moves are not changes, so an entity that only walks into a
 * condition, such as {@link EntityQuery#nearPlayer}, does not appear for a watch; wait for it with
 * {@link Entities#present} instead.</p>
 *
 * <p>Conditions may be any predicate; an {@link EntityQuery} also describes itself in the failure of a wait.</p>
 *
 * <pre>{@code
 * EntityWatch bubbles = entities.watch();
 * messages.chat("hello");
 * SeenEntity bubble = bubbles.appeared(EntityQuery.labelled("hello").and(EntityQuery.aroundPlayer(4)));
 * bubbles.disappeared(bubble, Duration.ofSeconds(10));
 * bubbles.neverAppeared(EntityQuery.labelled("hello"), Duration.ofSeconds(2));
 * }</pre>
 */
public interface EntityWatch {
	/**
	 * Waits, using the default timeout, for the next entity that appears satisfying a condition.
	 *
	 * @param matcher condition on the entity
	 * @return the entity as it was when it appeared
	 * @throws IllegalStateException when no such entity appears in time
	 */
	default @NotNull SeenEntity appeared(@NotNull Predicate<SeenEntity> matcher) {
		return appeared(matcher, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next entity that appears satisfying a condition. The following wait looks only at later changes.
	 *
	 * @param matcher condition on the entity
	 * @param timeout maximum wait
	 * @return the entity as it was when it appeared
	 * @throws IllegalStateException when no such entity appears in time
	 */
	@NotNull SeenEntity appeared(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, until the player no longer knows an entity.
	 *
	 * @param entity the entity, as a wait or {@link Entities#all()} returned it
	 * @throws IllegalStateException when the entity is still known after the timeout
	 */
	default void disappeared(@NotNull SeenEntity entity) {
		disappeared(entity, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the player no longer knows an entity: the server removed it, the player changed world or the
	 * connection ended. It returns at once for an entity already gone, and it does not move the watch.
	 *
	 * @param entity the entity, as a wait or {@link Entities#all()} returned it
	 * @param timeout maximum wait
	 * @throws IllegalStateException when the entity is still known after the timeout
	 */
	void disappeared(@NotNull SeenEntity entity, @NotNull Duration timeout);

	/**
	 * Expects that no entity appears satisfying a condition for a duration. It fails as soon as one has appeared
	 * since the previous wait, naming it, and returns normally once the duration has passed without one. It does not
	 * move the watch.
	 *
	 * @param matcher condition on the entity
	 * @param duration how long no such entity may appear
	 * @throws IllegalStateException when such an entity appears
	 */
	void neverAppeared(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration duration);

	/**
	 * Expects the next entity that appears satisfying a condition, for {@link SimulatedPlayer#awaitFirst}. Once it
	 * is met, {@link #appeared(Predicate, Duration)} with the same condition returns the entity at once.
	 *
	 * @param matcher condition on the entity
	 * @return expectation met once such an entity has appeared since the previous wait
	 */
	@NotNull Expectation expectAppeared(@NotNull Predicate<SeenEntity> matcher);

	/**
	 * Expects that the player no longer knows an entity, for {@link SimulatedPlayer#awaitFirst}.
	 *
	 * @param entity the entity, as a wait or {@link Entities#all()} returned it
	 * @return expectation met once the entity is gone
	 */
	@NotNull Expectation expectDisappeared(@NotNull SeenEntity entity);

	/**
	 * Returns the changes received since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread changes, oldest first
	 */
	@NotNull List<EntityChange> unread();
}
