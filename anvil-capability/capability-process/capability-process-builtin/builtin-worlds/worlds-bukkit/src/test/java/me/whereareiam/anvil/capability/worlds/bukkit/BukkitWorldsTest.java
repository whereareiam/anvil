package me.whereareiam.anvil.capability.worlds.bukkit;

import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChange;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitWorldsTest {
	private static final UUID ALICE = new UUID(0, 1);

	private final List<Location> teleports = new ArrayList<>();
	private final List<WorldCreator> creators = new ArrayList<>();
	private final List<World> unloaded = new ArrayList<>();
	private boolean refuse;
	private final WorldChangeLog log = new WorldChangeLog();

	@Test
	void namesTheWorldsOfAnOlderSpigotByTheRuleCraftBukkitNamesItsLevelsBy() {
		List<World> worlds = List.of(world("world", World.Environment.NORMAL, null),
				world("world_nether", World.Environment.NETHER, null),
				world("world_the_end", World.Environment.THE_END, null),
				world("Arena", World.Environment.NORMAL, null));
		BukkitWorlds bukkit = new BukkitWorlds(server(worlds, List.of()), log);

		assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "minecraft:arena"),
				bukkit.worlds().stream().map(AgentWorld::getKey).collect(Collectors.toList()));
		assertEquals(List.of("OVERWORLD", "NETHER", "END", "OVERWORLD"),
				bukkit.worlds().stream().map(AgentWorld::getKind).collect(Collectors.toList()));
	}

	@Test
	void namesTheKeyedWorldsOfPaperAndNewerSpigotByTheirOwnKeys() {
		World custom = world("dungeon", World.Environment.NORMAL, new NamespacedKey("example", "dungeon"));
		BukkitWorlds bukkit = new BukkitWorlds(server(List.of(world("world", World.Environment.NORMAL,
				NamespacedKey.minecraft("overworld")), custom), List.of()), log);

		assertEquals(List.of("minecraft:overworld", "example:dungeon"),
				bukkit.worlds().stream().map(AgentWorld::getKey).collect(Collectors.toList()));
	}

	@Test
	void reportsEachWorldsSpawnAndEachPlayersPlacement() {
		World world = world("world", World.Environment.NORMAL, null);
		Player alice = player(new Location(world, 1.5, 65, -2.5, 90, 15));
		BukkitWorlds bukkit = new BukkitWorlds(server(List.of(world), List.of(alice)), log);

		AgentLocation spawn = AgentLocation.builder().world("minecraft:overworld").x(0.5).y(64).z(0.5).build();
		assertEquals(spawn, bukkit.worlds().get(0).getSpawn());
		assertEquals(AgentLocation.builder().world("minecraft:overworld").x(1.5).y(65).z(-2.5).yaw(90).pitch(15).build(),
				bukkit.placements().get(0).getLocation());
		assertEquals(ALICE, bukkit.placements().get(0).getUniqueId());
	}

	@Test
	void teleportsAPlayerIntoTheWorldOfTheLocationsKey() {
		World world = world("world", World.Environment.NORMAL, null);
		World arena = world("arena", World.Environment.NORMAL, null);
		BukkitWorlds bukkit = new BukkitWorlds(server(List.of(world, arena), List.of(player(new Location(world, 0, 64, 0)))), log);

		assertTrue(bukkit.teleport(ALICE, AgentLocation.builder().world("minecraft:arena").x(3).y(70).z(4).yaw(180).build()));

		Location teleported = teleports.get(0);
		assertEquals(arena, teleported.getWorld());
		assertEquals(List.of(3D, 70D, 4D, 180F), List.of(teleported.getX(), teleported.getY(), teleported.getZ(),
				teleported.getYaw()));
	}

	@Test
	void refusesATeleportForAnAbsentPlayerOrAnUnloadedWorld() {
		World world = world("world", World.Environment.NORMAL, null);
		BukkitWorlds bukkit = new BukkitWorlds(server(List.of(world), List.of(player(new Location(world, 0, 64, 0)))), log);
		AgentLocation nether = AgentLocation.builder().world("minecraft:the_nether").build();

		assertThrows(IllegalArgumentException.class, () -> bukkit.teleport(new UUID(0, 2), nether));
		assertThrows(IllegalArgumentException.class, () -> bukkit.teleport(ALICE, nether));
		assertTrue(teleports.isEmpty());
	}

	@Test
	void recordsAWorldChangeWithTheKeysOfBothWorlds() {
		World world = world("world", World.Environment.NORMAL, null);
		World nether = world("world_nether", World.Environment.NETHER, null);
		Player alice = player(new Location(nether, 0, 64, 0));
		BukkitWorlds bukkit = new BukkitWorlds(server(List.of(world, nether), List.of(alice)), log);

		bukkit.changed(new PlayerChangedWorldEvent(alice, world));

		AgentWorldChange change = log.since(AgentWorldChangesRequest.builder().after(0).build()).getChanges().get(0);
		assertEquals(List.of("Alice", "minecraft:overworld", "minecraft:the_nether"),
				List.of(change.getUsername(), change.getFrom(), change.getTo()));
		assertFalse(change.getAt() <= 0);
	}

	@Test
	void createsAWorldThroughAWorldCreatorOfItsKindSeedAndGenerator() {
		List<World> worlds = new ArrayList<>(List.of(world("world", World.Environment.NORMAL, null)));
		BukkitWorlds bukkit = new BukkitWorlds(server(worlds, List.of()), log);

		AgentWorld created = bukkit.create(AgentWorldCreation.builder().key("minecraft:arena").kind("NETHER").seed(42L)
				.generator("FLAT").build());

		assertEquals(List.of("minecraft:arena", "NETHER"), List.of(created.getKey(), created.getKind()));
		WorldCreator creator = creators.get(0);
		assertEquals(List.of("arena", World.Environment.NETHER, 42L, WorldType.FLAT),
				List.of(creator.name(), creator.environment(), creator.seed(), creator.type()));
		assertNull(creator.generator());
		assertEquals(2, worlds.size());
	}

	@Test
	void createsAnEmptyWorldWhoseSpawnIsAboveTheOrigin() {
		World world = world("world", World.Environment.NORMAL, null);
		BukkitWorlds bukkit = new BukkitWorlds(server(new ArrayList<>(List.of(world)), List.of()), log);

		bukkit.create(AgentWorldCreation.builder().key("minecraft:void").kind("END").generator("EMPTY").build());

		WorldCreator creator = creators.get(0);
		assertEquals(List.of(World.Environment.THE_END, WorldType.NORMAL), List.of(creator.environment(), creator.type()));
		ChunkGenerator generator = assertInstanceOf(ChunkGenerator.class, creator.generator());
		Location spawn = generator.getFixedSpawnLocation(world, new Random());
		assertEquals(List.of(0D, 64D, 0D), List.of(spawn.getX(), spawn.getY(), spawn.getZ()));
	}

	@Test
	void refusesAKeyBukkitCannotNameAWorldByALoadedWorldAndACreationTheServerDidNotDo() {
		BukkitWorlds bukkit = new BukkitWorlds(server(new ArrayList<>(List.of(world("world", World.Environment.NORMAL,
				null))), List.of()), log);

		assertThrows(UnsupportedOperationException.class, () -> bukkit.create(creation("example:arena")));
		assertThrows(UnsupportedOperationException.class, () -> bukkit.create(creation("minecraft:arenas/one")));
		assertThrows(IllegalArgumentException.class, () -> bukkit.create(creation("minecraft:overworld")));
		assertTrue(creators.isEmpty());

		refuse = true;
		assertThrows(IllegalStateException.class, () -> bukkit.create(creation("minecraft:arena")));
	}

	@Test
	void unloadsAndSavesAWorldNobodyIsIn() {
		World main = world("world", World.Environment.NORMAL, null);
		World arena = world("arena", World.Environment.NORMAL, null);
		BukkitWorlds bukkit = new BukkitWorlds(server(new ArrayList<>(List.of(main, arena)), List.of()), log);

		bukkit.unload("minecraft:arena");

		assertEquals(List.of(arena), unloaded);
	}

	@Test
	void refusesToUnloadTheMainWorldAWorldPlayersAreInAndOneTheServerKept() {
		World main = world("world", World.Environment.NORMAL, null);
		World arena = world("arena", World.Environment.NORMAL, null);
		Player alice = player(new Location(arena, 0, 64, 0));
		World busy = world("busy", World.Environment.NORMAL, null, List.of(alice));
		BukkitWorlds bukkit = new BukkitWorlds(server(new ArrayList<>(List.of(main, arena, busy)), List.of(alice)), log);

		assertThrows(IllegalArgumentException.class, () -> bukkit.unload("minecraft:the_end"));
		assertThrows(IllegalStateException.class, () -> bukkit.unload("minecraft:overworld"));
		assertEquals("Players are in minecraft:busy: [Alice]",
				assertThrows(IllegalStateException.class, () -> bukkit.unload("minecraft:busy")).getMessage());
		refuse = true;
		assertThrows(IllegalStateException.class, () -> bukkit.unload("minecraft:arena"));
		assertTrue(unloaded.isEmpty());
	}

	private static AgentWorldCreation creation(String key) {
		return AgentWorldCreation.builder().key(key).kind("OVERWORLD").generator("DEFAULT").build();
	}

	private Server server(List<World> worlds, List<Player> players) {
		return proxy(Server.class, Map.of(
				"getWorlds", arguments -> worlds,
				"getOnlinePlayers", arguments -> players,
				"getPlayer", arguments -> players.stream().filter(player -> player.getUniqueId().equals(arguments[0]))
						.findFirst().orElse(null),
				"createWorld", arguments -> {
					if (refuse) return null;

					WorldCreator creator = (WorldCreator) arguments[0];
					creators.add(creator);
					World world = world(creator.name(), creator.environment(), null);
					worlds.add(world);
					return world;
				},
				"unloadWorld", arguments -> {
					if (refuse) return false;

					assertEquals(true, arguments[1]);
					unloaded.add((World) arguments[0]);
					return worlds.remove(arguments[0]);
				}
		));
	}

	private Player player(Location location) {
		return proxy(Player.class, Map.of(
				"getUniqueId", arguments -> ALICE,
				"getName", arguments -> "Alice",
				"getLocation", arguments -> location,
				"getWorld", arguments -> location.getWorld(),
				"teleport", arguments -> teleports.add((Location) arguments[0])
		));
	}

	private static World world(String name, World.Environment environment, NamespacedKey key) {
		return world(name, environment, key, List.of());
	}

	private static World world(String name, World.Environment environment, NamespacedKey key, List<Player> players) {
		Map<String, Answer> answers = new HashMap<>();
		answers.put("getPlayers", arguments -> players);
		answers.put("getName", arguments -> name);
		answers.put("getEnvironment", arguments -> environment);
		answers.put("getKey", arguments -> key);
		World[] world = new World[1];
		answers.put("getSpawnLocation", arguments -> new Location(world[0], 0.5, 64, 0.5));
		Class<?>[] types = key == null ? new Class<?>[]{World.class} : new Class<?>[]{World.class, Keyed.class};
		world[0] = (World) Proxy.newProxyInstance(BukkitWorldsTest.class.getClassLoader(), types, (proxy, method, arguments) -> {
			if (method.getName().equals("equals")) return proxy == arguments[0];
			if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
			if (method.getName().equals("toString")) return "World " + name;
			Answer answer = answers.get(method.getName());
			if (answer == null) throw new AssertionError("Unexpected call of World." + method.getName());

			return answer.answer(arguments);
		});
		return world[0];
	}

	private static <T> T proxy(Class<T> type, Map<String, Answer> answers) {
		return type.cast(Proxy.newProxyInstance(BukkitWorldsTest.class.getClassLoader(), new Class<?>[]{type},
				(proxy, method, arguments) -> {
					Answer answer = answers.get(method.getName());
					if (answer == null) throw new AssertionError("Unexpected call of " + type.getSimpleName() + "." + method.getName());

					return answer.answer(arguments);
				}));
	}

	@FunctionalInterface
	private interface Answer {
		Object answer(Object[] arguments);
	}
}
