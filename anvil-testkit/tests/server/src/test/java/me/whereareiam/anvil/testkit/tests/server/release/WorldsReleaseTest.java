package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.world.WorldStateWatch;
import me.whereareiam.anvil.capability.world.model.DimensionTypeInfo;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.model.SpawnPoint;
import me.whereareiam.anvil.capability.world.model.WorldBorder;
import me.whereareiam.anvil.capability.world.model.WorldState;
import me.whereareiam.anvil.capability.world.type.Environment;
import me.whereareiam.anvil.capability.world.type.LevelGeneration;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import me.whereareiam.anvil.capability.worlds.WorldChangeWatch;
import me.whereareiam.anvil.capability.worlds.WorldEventWatch;
import me.whereareiam.anvil.capability.worlds.Worlds;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.capability.worlds.type.WorldGenerator;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moves a player between the server's worlds through the server's own API and follows the move from both ends: the
 * server reports the world change, and the client is told the dimension it is in now. Creates worlds the way a plugin
 * does, follows the server loading and unloading them, and unloads, loads or deletes them again. The release's server
 * is kept for every test, so a world a test created and left loaded is deleted when the test passes, which the first
 * two tests check in that order.
 *
 * <p>It also changes the time, the weather, the world border and the spawn point through the server's console and
 * follows what the client is told; each test puts back what it changed, because the release's server is shared by
 * the task's tests.</p>
 */
@Release
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorldsReleaseTest {
	private static final String LEFT_BEHIND = "anvil_left_behind";

	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final String WORLD_CLOCKS = "26.1";
	// Above the nether's bedrock roof, where a player standing still is in the air instead of inside the rock.
	private static final WorldLocation ABOVE_THE_NETHER = WorldLocation.builder()
			.world("minecraft:the_nether")
			.x(0.5)
			.y(140)
			.z(0.5)
			.yaw(90)
			.build();

	@Test
	@Order(1)
	void leavesACreatedWorldLoadedWithAPlayerInIt(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);
		WorldChangeWatch changes = worlds.watch();

		worlds.create(WorldCreation.builder().key(LEFT_BEHIND).generator(WorldGenerator.FLAT).build());
		worlds.send(player, LEFT_BEHIND);

		changes.changed(player, LEFT_BEHIND, TIMEOUT);
	}

	@Test
	@Order(2)
	void findsNoWorldAnEarlierPassedTestCreated(ScenarioContext anvil) throws IOException {
		var server = anvil.processes().server("server");

		assertEquals(Optional.empty(), server.capability(Worlds.class).world(LEFT_BEHIND));
		assertFalse(server.files().exists(LEFT_BEHIND));
		assertFalse(server.files().exists("world/dimensions/minecraft/" + LEFT_BEHIND));
	}

	@Test
	void listsTheServersWorldsByTheKeysItsPlayersAreTold(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);

		assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
				worlds.worlds().stream().map(ServerWorld::getKey).toList());
		assertEquals(List.of(WorldKind.OVERWORLD, WorldKind.NETHER, WorldKind.END),
				worlds.worlds().stream().map(ServerWorld::getKind).toList());
		assertEquals(Optional.of("minecraft:overworld"), player.capability(World.class).dimension());
		assertEquals("minecraft:overworld", worlds.location(player).orElseThrow().getWorld());
		World world = player.capability(World.class);
		DimensionTypeInfo overworld = new DimensionTypeInfo("minecraft:overworld", Environment.OVERWORLD);
		assertEquals(new LevelInfo("minecraft:overworld", overworld, LevelGeneration.NORMAL), world.level().orElseThrow());
		assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
				world.server().orElseThrow().getLevels());
	}

	@Test
	void movesAPlayerToAnotherWorldAndBack(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);
		World world = player.capability(World.class);
		WorldChangeWatch changes = worlds.watch();
		DimensionWatch dimensions = world.watch();

		worlds.send(player, ABOVE_THE_NETHER);

		assertEquals("minecraft:overworld", changes.changed(player, "the_nether", TIMEOUT).getFrom());
		assertEquals("minecraft:overworld", dimensions.entered("minecraft:the_nether", TIMEOUT).getFrom());
		assertEquals(Optional.of("minecraft:the_nether"), world.dimension());
		assertEquals(new DimensionTypeInfo("minecraft:the_nether", Environment.NETHER),
				world.level().orElseThrow().getType());
		WorldLocation location = worlds.location(player).orElseThrow();
		assertEquals(List.of("minecraft:the_nether", 140D), List.of(location.getWorld(), location.getY()));

		worlds.send(player, "overworld");

		changes.changed(player, "minecraft:overworld", TIMEOUT);
		dimensions.entered("overworld", TIMEOUT);
		assertEquals(Optional.of("minecraft:overworld"), world.dimension());
	}

	@Test
	void createsAWorldSendsAPlayerIntoItAndUnloadsAndLoadsItOnceThePlayerLeft(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);
		World world = player.capability(World.class);
		WorldChangeWatch changes = worlds.watch();
		DimensionWatch dimensions = world.watch();
		WorldEventWatch events = worlds.watchWorlds();

		ServerWorld flat = worlds.create(WorldCreation.builder()
				.key("anvil_flat")
				.seed(42L)
				.generator(WorldGenerator.FLAT)
				.build());

		assertEquals(List.of("minecraft:anvil_flat", WorldKind.OVERWORLD), List.of(flat.getKey(), flat.getKind()));
		assertEquals(Optional.of(flat), worlds.world("anvil_flat"));
		events.loaded("minecraft:anvil_flat", TIMEOUT);
		worlds.send(player, "anvil_flat");
		changes.changed(player, "minecraft:anvil_flat", TIMEOUT);
		dimensions.entered("minecraft:anvil_flat", TIMEOUT);
		assertThrows(IllegalStateException.class, () -> worlds.unload("anvil_flat"));

		worlds.send(player, "overworld");
		changes.changed(player, "minecraft:overworld", TIMEOUT);
		worlds.unload("anvil_flat");

		events.unloaded("anvil_flat", TIMEOUT);
		assertEquals(Optional.empty(), worlds.world("anvil_flat"));
		assertThrows(IllegalStateException.class, () -> worlds.unload("overworld"));

		ServerWorld loaded = worlds.load("anvil_flat");
		events.loaded("anvil_flat", TIMEOUT);
		assertEquals(List.of("minecraft:anvil_flat", WorldKind.OVERWORLD), List.of(loaded.getKey(), loaded.getKind()));
		worlds.delete("anvil_flat");
		assertThrows(IllegalArgumentException.class, () -> worlds.load("anvil_flat"));
	}

	@Test
	void createsAnEmptyWorldOfAnotherKindAndDeletesItsFolder(ScenarioContext anvil) throws IOException {
		var server = anvil.processes().server("server");
		Worlds worlds = server.capability(Worlds.class);
		WorldEventWatch events = worlds.watchWorlds();

		ServerWorld empty = worlds.create(WorldCreation.builder()
				.key("anvil_empty_nether")
				.kind(WorldKind.NETHER)
				.generator(WorldGenerator.EMPTY)
				.build());

		assertEquals(List.of("minecraft:anvil_empty_nether", WorldKind.NETHER), List.of(empty.getKey(), empty.getKind()));
		assertEquals(List.of(0D, 64D, 0D), List.of(empty.getSpawn().getX(), empty.getSpawn().getY(), empty.getSpawn().getZ()));
		// Since 26.1 a server keeps every dimension below its main world's folder.
		String folder = server.files().exists("anvil_empty_nether") ? "anvil_empty_nether"
				: "world/dimensions/minecraft/anvil_empty_nether";
		assertTrue(server.files().exists(folder));
		worlds.delete("anvil_empty_nether");

		events.unloaded("anvil_empty_nether", TIMEOUT);
		assertEquals(Optional.empty(), worlds.world("anvil_empty_nether"));
		assertFalse(server.files().exists(folder));
	}

	@Test
	void followsTheTimeOfDayOfThePlayersLevel(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		World world = server.join().capability(World.class);

		server.console("time set 6000");

		WorldState noon = world.states().until(state -> state.getTime() != null
				&& state.getTime().timeOfDay().orElse(-1) >= 6000 && state.getTime().timeOfDay().orElse(-1) < 8000, TIMEOUT);
		assertTrue(noon.getTime().isAdvancing());
		assertEquals(TestedRelease.isAtLeast(WORLD_CLOCKS), noon.getTime().getClocks().containsKey("minecraft:overworld"));
	}

	@Test
	void followsTheWeatherAsItRainsAndThunders(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		World world = server.join().capability(World.class);
		WorldStateWatch states = world.states();
		assertEquals(WeatherType.CLEAR, states.until(state -> state.getWeather() != null, TIMEOUT).weatherType());

		try {
			server.console("weather thunder");
			states.until(state -> state.weatherType() == WeatherType.THUNDER, TIMEOUT);
		} finally {
			server.console("weather clear");
		}

		states.until(state -> state.weatherType() == WeatherType.CLEAR, TIMEOUT);
	}

	@Test
	void followsTheWorldBorderOfThePlayersLevel(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		World world = server.join().capability(World.class);
		WorldBorder initial = world.states().until(state -> state.getBorder() != null, TIMEOUT).getBorder();

		try {
			// Whole coordinates center the border on the middle of a block, so the test names the middle itself.
			server.console("worldborder center 10.5 20.5");
			server.console("worldborder set 200");
			WorldBorder border = world.states().until(state -> state.getBorder().getSize() == 200
					&& state.getBorder().getCenterX() == 10.5, TIMEOUT).getBorder();
			assertEquals(List.of(10.5D, 20.5D, 0L), List.of(border.getCenterX(), border.getCenterZ(), border.getLerpTicks()));
		} finally {
			server.console("worldborder center " + initial.getCenterX() + " " + initial.getCenterZ());
			server.console("worldborder set " + BigDecimal.valueOf(initial.getSize()).toPlainString());
		}

		world.states().until(state -> state.getBorder().getSize() == initial.getSize(), TIMEOUT);
	}

	@Test
	void followsTheSpawnPointOfTheWorld(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		World world = server.join().capability(World.class);
		SpawnPoint initial = world.states().until(state -> state.getSpawn() != null, TIMEOUT).getSpawn();
		assertEquals("minecraft:overworld", initial.getDimension());

		try {
			server.console("setworldspawn " + (initial.getX() + 1) + " " + initial.getY() + " " + initial.getZ());
			SpawnPoint moved = world.states().until(state -> state.getSpawn().getX() == initial.getX() + 1, TIMEOUT)
					.getSpawn();
			assertEquals(List.of("minecraft:overworld", initial.getY(), initial.getZ()),
					List.of(moved.getDimension(), moved.getY(), moved.getZ()));
		} finally {
			server.console("setworldspawn " + initial.getX() + " " + initial.getY() + " " + initial.getZ());
		}
	}
}
