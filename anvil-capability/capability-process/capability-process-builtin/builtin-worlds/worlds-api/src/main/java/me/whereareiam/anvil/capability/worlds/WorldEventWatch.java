package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.worlds.model.WorldEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Follows a server loading and unloading its worlds, as the server reports it, from the moment the watch was created:
 * worlds a plugin creates on its own, and those a test creates, unloads or deletes. Each wait continues after the event
 * the previous one found, so a world loaded again is matched once per wait and never by an event from before the
 * watch. A world is named by its level's key, with or without its {@code minecraft} namespace.
 *
 * <pre>{@code
 * WorldEventWatch events = lobby.capability(Worlds.class).watchWorlds();
 * messages.command("tpworld arena_nether");
 * events.loaded("minecraft:arena_nether");
 * changes.changed(alice, "minecraft:arena_nether");
 * }</pre>
 */
public interface WorldEventWatch {
	/**
	 * Waits, using the default timeout, for the server to load a world.
	 *
	 * @param world the key of the world's level
	 * @return the event
	 */
	default @NotNull WorldEvent loaded(@NotNull String world) {
		return loaded(world, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the server to load a world.
	 *
	 * @param world the key of the world's level
	 * @param timeout maximum wait
	 * @return the event
	 * @throws IllegalStateException when the server does not load the world in time
	 */
	@NotNull WorldEvent loaded(@NotNull String world, @NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, for the server to unload a world.
	 *
	 * @param world the key of the world's level
	 * @return the event
	 */
	default @NotNull WorldEvent unloaded(@NotNull String world) {
		return unloaded(world, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the server to unload a world.
	 *
	 * @param world the key of the world's level
	 * @param timeout maximum wait
	 * @return the event
	 * @throws IllegalStateException when the server does not unload the world in time
	 */
	@NotNull WorldEvent unloaded(@NotNull String world, @NotNull Duration timeout);

	/**
	 * Expects that the server does not load a world for a duration. It fails as soon as the server has loaded the
	 * world since the previous wait, and returns normally once the duration has passed without that. It does not move
	 * the watch.
	 *
	 * @param world the key of the world's level
	 * @param duration how long the server must not load the world
	 * @throws IllegalStateException when the server loads the world
	 */
	void neverLoaded(@NotNull String world, @NotNull Duration duration);

	/**
	 * Expects the server to load a world, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #loaded(String, Duration)} for the same world returns the event at once.
	 *
	 * @param world the key of the world's level
	 * @return expectation met once the server has loaded the world since the previous wait
	 */
	@NotNull Expectation expectLoaded(@NotNull String world);

	/**
	 * Returns the world events since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread events, oldest first
	 */
	@NotNull List<WorldEvent> unread();
}
