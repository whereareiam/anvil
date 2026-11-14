package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * What one server knows about its worlds and the players in them, moving a player to another world, and creating and
 * unloading worlds, through the server's own API: Bukkit's on Paper and Spigot, and Minecraft's own on NeoForge. A
 * proxy has no worlds, so it has no {@code Worlds}. NeoForge has no API to create or unload a level while the server
 * runs, so only Paper and Spigot create and unload worlds.
 *
 * <p>A world is named by the key of its level, with its namespace, such as {@code minecraft:the_nether}: the same key
 * the player's client is told, which its {@code World} capability reports. A key without a namespace, such as
 * {@code arena}, is in the {@code minecraft} namespace, as in the game's commands. A simulated player is found by
 * every profile its client was granted, as the server's {@code Players} finds it.</p>
 *
 * <pre>{@code
 * Worlds worlds = context.processes().server("lobby").capability(Worlds.class);
 * WorldChangeWatch changes = worlds.watch();
 * worlds.send(alice, "minecraft:the_nether");
 * changes.changed(alice, "minecraft:the_nether");
 * assertEquals("minecraft:the_nether", worlds.location(alice).orElseThrow().getWorld());
 * }</pre>
 */
public interface Worlds extends ProcessCapability {
	/**
	 * Returns the worlds the server has loaded now, in the server's order, which starts with its main world.
	 *
	 * @return the worlds
	 */
	@NotNull List<ServerWorld> worlds();

	/**
	 * Returns one world the server has loaded now.
	 *
	 * @param key the key of the world's level
	 * @return the world, or empty while the server has not loaded it
	 */
	@NotNull Optional<ServerWorld> world(@NotNull String key);

	/**
	 * Returns where the server says a player is now.
	 *
	 * @param player the simulated player
	 * @return the player's world, position and view, or empty while the player is not on the server
	 */
	@NotNull Optional<WorldLocation> location(@NotNull SimulatedPlayer player);

	/**
	 * Moves a player to the spawn of one of the server's worlds, as a plugin teleporting it there does.
	 *
	 * @param player the simulated player
	 * @param world the key of the world's level
	 * @throws IllegalArgumentException when the server has not loaded the world
	 * @throws IllegalStateException when the player is not on the server, or the server refused to move it, for
	 *         example because a plugin cancelled the teleport
	 */
	void send(@NotNull SimulatedPlayer player, @NotNull String world);

	/**
	 * Moves a player to a location in one of the server's worlds, as a plugin teleporting it there does.
	 *
	 * @param player the simulated player
	 * @param location the world, position and view to move the player to
	 * @throws IllegalArgumentException when the server has not loaded the location's world
	 * @throws IllegalStateException when the player is not on the server, or the server refused to move it, for
	 *         example because a plugin cancelled the teleport
	 */
	void send(@NotNull SimulatedPlayer player, @NotNull WorldLocation location);

	/**
	 * Creates a world and loads it, as a plugin creating one through the server's API does, and returns it as the
	 * server reports it. The world stays until a test unloads it or the server stops; a server that a scenario keeps
	 * for later scenarios keeps it as well. Its folder stays in the server's workspace, so creating it again on the same
	 * server loads what it kept.
	 *
	 * @param creation the world's key, kind, seed and generator
	 * @return the world the server created
	 * @throws IllegalArgumentException when the server has already loaded a world of that key, or the kind is
	 *         {@link me.whereareiam.anvil.capability.worlds.type.WorldKind#CUSTOM}
	 * @throws UnsupportedOperationException when the server cannot create such a world: a NeoForge server, which has no
	 *         API to create a level while it runs, or a key Paper and Spigot cannot name a world by
	 * @throws IllegalStateException when the server refused to create the world
	 */
	@NotNull ServerWorld create(@NotNull WorldCreation creation);

	/**
	 * Saves a world and unloads it, as a plugin unloading it through the server's API does. The world's folder stays in
	 * the server's workspace.
	 *
	 * @param world the key of the world's level
	 * @throws IllegalArgumentException when the server has not loaded the world
	 * @throws UnsupportedOperationException when the server cannot unload a world: a NeoForge server
	 * @throws IllegalStateException when the server refused to unload it: the server's main world, a world players are
	 *         in, or one a plugin kept loaded
	 */
	void unload(@NotNull String world);

	/**
	 * Starts following the players moving between the server's worlds from now on. Each watch keeps its own
	 * position, so several can follow the same server independently.
	 *
	 * @return a watch that starts after the last world change reported so far
	 */
	@NotNull WorldChangeWatch watch();
}
