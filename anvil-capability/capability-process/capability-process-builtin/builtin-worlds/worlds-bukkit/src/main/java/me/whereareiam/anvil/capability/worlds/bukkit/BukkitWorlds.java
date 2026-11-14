package me.whereareiam.anvil.capability.worlds.bukkit;

import me.whereareiam.anvil.capability.worlds.agent.ServerWorlds;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Bukkit server's worlds, named by the keys of their levels, as the server tells its players' clients. A server
 * whose worlds are {@link Keyed}, Paper's and newer Spigot's, reports each key itself. On an older Spigot, whose
 * worlds are not, the key follows the rule CraftBukkit names its levels by: the main world and the nether and end
 * worlds named after it have the vanilla keys, and every other world the key of its lower-case name in the
 * {@code minecraft} namespace.
 */
final class BukkitWorlds implements ServerWorlds, Listener {
	private final Server server;
	private final WorldChangeLog changes;

	BukkitWorlds(@NotNull Server server, @NotNull WorldChangeLog changes) {
		this.server = server;
		this.changes = changes;
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

		World world = server.getWorlds().stream()
				.filter(loaded -> key(loaded).equals(location.getWorld()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("The server has not loaded the world " + location.getWorld()));
		return player.teleport(new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(),
				location.getPitch()));
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
}
