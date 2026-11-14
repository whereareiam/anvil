package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.world.DimensionWatch;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.worlds.WorldChangeWatch;
import me.whereareiam.anvil.capability.worlds.Worlds;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.capability.worlds.type.WorldGenerator;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Moves a player between the server's worlds through the server's own API and follows the move from both ends: the
 * server reports the world change, and the client is told the dimension it is in now. Creates worlds the way a plugin
 * does and unloads them again, since the release's server is kept for every test.
 */
@Release
class WorldsReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	// Above the nether's bedrock roof, where a player standing still is in the air instead of inside the rock.
	private static final WorldLocation ABOVE_THE_NETHER = WorldLocation.builder()
			.world("minecraft:the_nether")
			.x(0.5)
			.y(140)
			.z(0.5)
			.yaw(90)
			.build();

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
		WorldLocation location = worlds.location(player).orElseThrow();
		assertEquals(List.of("minecraft:the_nether", 140D), List.of(location.getWorld(), location.getY()));

		worlds.send(player, "overworld");

		changes.changed(player, "minecraft:overworld", TIMEOUT);
		dimensions.entered("overworld", TIMEOUT);
		assertEquals(Optional.of("minecraft:overworld"), world.dimension());
	}

	@Test
	void createsAWorldSendsAPlayerIntoItAndUnloadsItOnceThePlayerLeft(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);
		World world = player.capability(World.class);
		WorldChangeWatch changes = worlds.watch();
		DimensionWatch dimensions = world.watch();

		ServerWorld flat = worlds.create(WorldCreation.builder()
				.key("anvil_flat")
				.seed(42L)
				.generator(WorldGenerator.FLAT)
				.build());

		assertEquals(List.of("minecraft:anvil_flat", WorldKind.OVERWORLD), List.of(flat.getKey(), flat.getKind()));
		assertEquals(Optional.of(flat), worlds.world("anvil_flat"));
		worlds.send(player, "anvil_flat");
		changes.changed(player, "minecraft:anvil_flat", TIMEOUT);
		dimensions.entered("minecraft:anvil_flat", TIMEOUT);
		assertThrows(IllegalStateException.class, () -> worlds.unload("anvil_flat"));

		worlds.send(player, "overworld");
		changes.changed(player, "minecraft:overworld", TIMEOUT);
		worlds.unload("anvil_flat");

		assertEquals(Optional.empty(), worlds.world("anvil_flat"));
		assertThrows(IllegalStateException.class, () -> worlds.unload("overworld"));
	}

	@Test
	void createsAnEmptyWorldOfAnotherKind(ScenarioContext anvil) {
		Worlds worlds = anvil.processes().server("server").capability(Worlds.class);

		ServerWorld empty = worlds.create(WorldCreation.builder()
				.key("anvil_empty_nether")
				.kind(WorldKind.NETHER)
				.generator(WorldGenerator.EMPTY)
				.build());

		assertEquals(List.of("minecraft:anvil_empty_nether", WorldKind.NETHER), List.of(empty.getKey(), empty.getKind()));
		assertEquals(List.of(0D, 64D, 0D), List.of(empty.getSpawn().getX(), empty.getSpawn().getY(), empty.getSpawn().getZ()));
		worlds.unload("anvil_empty_nether");
		assertEquals(Optional.empty(), worlds.world("anvil_empty_nether"));
	}
}
