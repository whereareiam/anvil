package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tracks the entities the server shows the player, as the client knows them: where each one is and which way it
 * faces, what rides what, the custom name, display text and item each one shows, and the flags a client sees, such as
 * invisible, glowing, burning and sneaking.
 *
 * <p>An entity is known from the packet that spawns it until the packet that removes it, a respawn or a change of
 * world, or the end of the connection. Every connection starts with no entities.</p>
 *
 * <p>The capability separates the facts about an entity from its continuous state. A spawn, a removal and a change
 * of what an entity shows or rides are facts, which a {@link #watch() watch} follows in order. Where an entity is and
 * which way it faces change with every move, so they are never sent on their own: a fact carries them as they were
 * at that moment, and {@link #all()} and {@link #present} read them as they are now.</p>
 *
 * <pre>{@code
 * Entities entities = player.capability(Entities.class);
 * EntityWatch bubbles = entities.watch();
 * messages.chat("hello");
 * SeenEntity bubble = bubbles.appeared(EntityQuery.labelled("hello").and(EntityQuery.aroundPlayer(4)));
 * bubbles.disappeared(bubble, Duration.ofSeconds(10));
 * }</pre>
 */
public interface Entities extends PlayerCapability {
	/**
	 * Returns the entities the player knows now, without waiting, in the order they appeared, each where it is now and
	 * measured against where the player is now.
	 *
	 * @return the known entities of the current connection
	 */
	@NotNull List<SeenEntity> all();

	/**
	 * Waits, using the default timeout, until the player knows an entity that satisfies a condition now.
	 *
	 * @param matcher condition on the entity as it is now
	 * @return the first such entity
	 * @throws IllegalStateException when no known entity satisfies the condition in time
	 */
	default @NotNull SeenEntity present(@NotNull Predicate<SeenEntity> matcher) {
		return present(matcher, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the player knows an entity that satisfies a condition now, such as a mob that walks up to the
	 * player. Unlike a watch, it reads the current state on every check, so it also sees conditions that only moves
	 * make true, and it returns at once for an entity that already satisfies the condition.
	 *
	 * <pre>{@code
	 * SeenEntity villager = entities.present(EntityQuery.ofType("villager").and(EntityQuery.nearPlayer(3)),
	 *         Duration.ofSeconds(20));
	 * }</pre>
	 *
	 * @param matcher condition on the entity as it is now
	 * @param timeout maximum wait
	 * @return the first such entity, as it was when the wait found it
	 * @throws IllegalStateException when no known entity satisfies the condition in time
	 */
	@NotNull SeenEntity present(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration timeout);

	/**
	 * Starts following the entities the player sees from now on. Each watch keeps its own position, so several can
	 * follow the same player independently.
	 *
	 * @return a watch that starts after the last change received so far
	 */
	@NotNull EntityWatch watch();
}
