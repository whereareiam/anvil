package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.model.ServerInfo;
import me.whereareiam.anvil.capability.world.model.SpawnPoint;
import me.whereareiam.anvil.capability.world.model.Weather;
import me.whereareiam.anvil.capability.world.model.WorldBorder;
import me.whereareiam.anvil.capability.world.model.WorldTime;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Observes the world the player is in as its client knows it: the dimension of the level the server put it in, named
 * by the level's key with its namespace, such as {@code minecraft:overworld}, {@code minecraft:the_nether} or, for a
 * world a plugin created, {@code minecraft:arena}, with the level's dimension type, environment and generation, and
 * the server's hardcore mode and levels. A client learns them when it joins a level and each time the server respawns
 * it, which is how a server moves a player to another dimension; it never chooses one itself. It also tells what the
 * client was told about the world around its level: the time, the weather, the world border and the spawn point, which
 * {@link #states()} waits for.
 *
 * <p>Every change is kept in order and across reconnects. What a server knows about the worlds of its players, and
 * moving a player to another world, belongs to that server's {@code Worlds} capability.</p>
 *
 * <pre>{@code
 * World world = alice.capability(World.class);
 * DimensionWatch dimensions = world.watch();
 * lobby.capability(Worlds.class).send(alice, "minecraft:the_nether");
 * dimensions.entered("minecraft:the_nether");
 * assertEquals(Optional.of("minecraft:the_nether"), world.dimension());
 *
 * lobby.console().sendCommand("weather rain");
 * world.states().until(state -> state.weatherType() == WeatherType.RAIN);
 * }</pre>
 */
public interface World extends PlayerCapability {
	/**
	 * Returns the dimension the player is in now, without waiting.
	 *
	 * @return key of the player's level, or empty while the player is not connected or has not joined a level yet
	 */
	@NotNull Optional<String> dimension();

	/**
	 * Returns what the client was told about the level it is in now, without waiting.
	 *
	 * <pre>{@code
	 * LevelInfo level = world.level().orElseThrow();
	 * assertEquals(Environment.NETHER, level.getType().getEnvironment());
	 * assertEquals(LevelGeneration.FLAT, level.getGeneration());
	 * }</pre>
	 *
	 * @return the player's level, or empty while the player is not connected or has not joined a level yet
	 */
	@NotNull Optional<LevelInfo> level();

	/**
	 * Returns what the client was told about the server's worlds when it joined the server it is on now, without
	 * waiting. A proxy that moves the player to another server replaces it.
	 *
	 * <pre>{@code
	 * ServerInfo server = world.server().orElseThrow();
	 * assertTrue(server.getLevels().contains("minecraft:arena"));
	 * }</pre>
	 *
	 * @return the server's worlds, or empty while the player is not connected or has not joined a level yet
	 */
	@NotNull Optional<ServerInfo> server();

	/**
	 * Returns every dimension change so far, without waiting.
	 *
	 * @return immutable history, oldest first
	 */
	@NotNull List<DimensionChange> changes();

	/**
	 * Starts following the dimension changes from now on. Each watch keeps its own position.
	 *
	 * @return a watch that starts after the last change so far
	 */
	@NotNull DimensionWatch watch();

	/**
	 * Returns the time the client was told last, without waiting.
	 *
	 * <pre>{@code
	 * lobby.console().sendCommand("time set noon");
	 * world.states().until(state -> state.getTime() != null
	 *         && state.getTime().timeOfDay().orElse(-1) >= 6000);
	 * }</pre>
	 *
	 * @return the time, or empty while the player is not in a level or the server has not sent it yet
	 */
	default @NotNull Optional<WorldTime> time() {
		return Optional.ofNullable(states().current().getTime());
	}

	/**
	 * Returns the weather of the player's level, without waiting.
	 *
	 * @return the weather, clear until the server says otherwise, or empty while the player is not in a level
	 */
	default @NotNull Optional<Weather> weather() {
		return Optional.ofNullable(states().current().getWeather());
	}

	/**
	 * Returns the world border of the player's level, without waiting.
	 *
	 * @return the border, or empty while the player is not in a level or the server has not sent it for the level yet
	 */
	default @NotNull Optional<WorldBorder> border() {
		return Optional.ofNullable(states().current().getBorder());
	}

	/**
	 * Returns the world's spawn point, without waiting.
	 *
	 * @return the spawn point, or empty while the player is not connected or the server has not sent it yet
	 */
	default @NotNull Optional<SpawnPoint> spawn() {
		return Optional.ofNullable(states().current().getSpawn());
	}

	/**
	 * Returns what the client knows about the world around its level now, and waits for it to change.
	 *
	 * @return the player's world state
	 */
	@NotNull WorldStateWatch states();
}
