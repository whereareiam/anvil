package me.whereareiam.anvil.capability.worlds.bukkit;

import me.whereareiam.anvil.capability.worlds.agent.ServerWorlds;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldEventLog;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Bukkit server's worlds, named by the keys of their levels, as the server tells its players' clients. A server
 * whose worlds are {@link Keyed}, Paper's and newer Spigot's, reports each key itself. On an older Spigot, whose
 * worlds are not, the key follows the rule CraftBukkit names its levels by: the main world and the nether and end
 * worlds named after it have the vanilla keys, and every other world the key of its lower-case name in the
 * {@code minecraft} namespace.
 *
 * <p>A world is created under the name of its key's path, which both rules name by that key in the {@code minecraft}
 * namespace, so only such keys can be created.</p>
 */
final class BukkitWorlds implements ServerWorlds, Listener {
	private final Server server;
	private final WorldChangeLog changes;
	private final WorldEventLog events;

	BukkitWorlds(@NotNull Server server, @NotNull WorldChangeLog changes, @NotNull WorldEventLog events) {
		this.server = server;
		this.changes = changes;
		this.events = events;
	}

	@Override
	public @NotNull List<AgentWorld> worlds() {
		return server.getWorlds().stream().map(this::world).collect(Collectors.toUnmodifiableList());
	}

	@Override
	public @NotNull List<AgentPlacement> placements() {
		return server.getOnlinePlayers().stream()
				.map(player -> AgentPlacement.builder()
						.uniqueId(player.getUniqueId())
						.location(location(player.getLocation()))
						.build())
				.collect(Collectors.toUnmodifiableList());
	}

	@Override
	public boolean teleport(@NotNull UUID uniqueId, @NotNull AgentLocation location) {
		Player player = server.getPlayer(uniqueId);
		if (player == null) throw new IllegalArgumentException("No player " + uniqueId + " is on the server");

		World world = find(location.getWorld());
		if (world == null) throw new IllegalArgumentException("The server has not loaded the world " + location.getWorld());

		return player.teleport(new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(),
				location.getPitch()));
	}

	@Override
	public @NotNull AgentWorld create(@NotNull AgentWorldCreation creation) {
		String key = creation.getKey();
		if (find(key) != null) throw new IllegalArgumentException("The server has already loaded the world " + key);

		WorldCreator creator = new WorldCreator(name(key)).environment(environment(creation.getKind()));
		if (creation.getSeed() != null) creator.seed(creation.getSeed());
		if (creation.getGenerator().equals("FLAT")) creator.type(WorldType.FLAT);
		if (creation.getGenerator().equals("EMPTY")) creator.generator(new EmptyGenerator());

		World world = server.createWorld(creator);
		if (world == null) throw new IllegalStateException("The server did not create the world " + key);

		return world(world);
	}

	@Override
	public void unload(@NotNull String key) {
		if (!server.unloadWorld(unloadable(key), true))
			throw new IllegalStateException("The server did not unload " + key + ", for example because a plugin cancelled it");
	}

	@Override
	public void delete(@NotNull String key) {
		World world = unloadable(key);
		Path folder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
		Path container = server.getWorldContainer().toPath().toAbsolutePath().normalize();
		if (!folder.startsWith(container) || folder.equals(container))
			throw new IllegalStateException("The folder of " + key + " is outside the server's worlds: " + folder);
		if (!server.unloadWorld(world, false))
			throw new IllegalStateException("The server did not unload " + key + ", for example because a plugin cancelled it");

		try (Stream<Path> files = Files.walk(folder)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).collect(Collectors.toList()))
				Files.delete(file);
		} catch (IOException exception) {
			throw new IllegalStateException("The server unloaded " + key + " but its folder " + folder
					+ " could not be deleted: " + exception, exception);
		}
	}

	/**
	 * Records a player's world change once every other plugin has seen it.
	 *
	 * @param event the change
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void changed(@NotNull PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		changes.record(player.getUniqueId(), player.getName(), key(event.getFrom()), key(player.getWorld()));
	}

	/**
	 * Records the server loading a world.
	 *
	 * @param event the load
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void loaded(@NotNull WorldLoadEvent event) {
		events.loaded(key(event.getWorld()));
	}

	/**
	 * Records the server unloading a world, unless a plugin kept it loaded.
	 *
	 * @param event the unload
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void unloaded(@NotNull WorldUnloadEvent event) {
		events.unloaded(key(event.getWorld()));
	}

	/**
	 * Finds a world the server could unload: one it has loaded, other than its main world, that no player is in.
	 */
	private @NotNull World unloadable(@NotNull String key) {
		World world = find(key);
		if (world == null) throw new IllegalArgumentException("The server has not loaded the world " + key);
		if (world.equals(server.getWorlds().get(0)))
			throw new IllegalStateException(key + " is the server's main world, which it never unloads");

		List<String> players = world.getPlayers().stream().map(Player::getName).collect(Collectors.toList());
		if (!players.isEmpty()) throw new IllegalStateException("Players are in " + key + ": " + players);

		return world;
	}

	private @Nullable World find(@NotNull String key) {
		return server.getWorlds().stream().filter(loaded -> key(loaded).equals(key)).findFirst().orElse(null);
	}

	private @NotNull AgentWorld world(@NotNull World world) {
		return AgentWorld.builder()
				.key(key(world))
				.kind(kind(world))
				.spawn(location(world.getSpawnLocation()))
				.build();
	}

	private @NotNull AgentLocation location(@NotNull Location location) {
		return AgentLocation.builder()
				.world(key(location.getWorld()))
				.x(location.getX())
				.y(location.getY())
				.z(location.getZ())
				.yaw(location.getYaw())
				.pitch(location.getPitch())
				.build();
	}

	private @NotNull String key(@NotNull World world) {
		if (world instanceof Keyed) return ((Keyed) world).getKey().toString();

		String main = server.getWorlds().get(0).getName();
		String name = world.getName();
		if (name.equals(main)) return "minecraft:overworld";
		if (name.equals(main + "_nether")) return "minecraft:the_nether";
		if (name.equals(main + "_the_end")) return "minecraft:the_end";

		return "minecraft:" + name.toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Names a world by its key's path, which Bukkit names it by in the {@code minecraft} namespace.
	 */
	private static @NotNull String name(@NotNull String key) {
		int colon = key.indexOf(':');
		String namespace = key.substring(0, colon);
		String path = key.substring(colon + 1);
		if (!namespace.equals("minecraft") || path.contains("/"))
			throw new UnsupportedOperationException("Bukkit creates a world under a plain name, which it names in the "
					+ "minecraft namespace, so it cannot create " + key);

		return path;
	}

	private static World.@NotNull Environment environment(@NotNull String kind) {
		switch (kind) {
			case "NETHER":
				return World.Environment.NETHER;
			case "END":
				return World.Environment.THE_END;
			default:
				return World.Environment.NORMAL;
		}
	}

	private static @NotNull String kind(@NotNull World world) {
		switch (world.getEnvironment()) {
			case NORMAL:
				return "OVERWORLD";
			case NETHER:
				return "NETHER";
			case THE_END:
				return "END";
			default:
				return "CUSTOM";
		}
	}

	/**
	 * Generates no blocks. It answers through the chunk method every release keeps, and newer releases generate
	 * nothing either, because a generator generates none of the vanilla steps unless it asks for them.
	 */
	private static final class EmptyGenerator extends ChunkGenerator {
		@Override
		public @NotNull ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z,
				@NotNull BiomeGrid biome) {
			return createChunkData(world);
		}

		@Override
		public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
			return new Location(world, 0, 64, 0);
		}
	}
}
