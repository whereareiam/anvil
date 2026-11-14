package me.whereareiam.anvil.capability.worlds.model;

import lombok.Value;
import me.whereareiam.anvil.capability.worlds.type.WorldEventType;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * A server loading or unloading one of its worlds while it runs, as the server reports it. The worlds it loads while
 * it starts are no events.
 */
@Value
public class WorldEvent {
	/**
	 * The key of the world's level, with its namespace.
	 */
	@NotNull String world;
	/**
	 * Whether the server loaded or unloaded the world.
	 */
	@NotNull WorldEventType type;
	/**
	 * When the server reported the event.
	 */
	@NotNull Instant at;
}
