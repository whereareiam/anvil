package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.world.BlockQuery;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.world.WorldWatch;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knows the block the player stands on, as the server holds it, and sees a {@code setblock} change it.
 */
@Release
class WorldReleaseTest {
	/**
	 * The first release Anvil ships no block data for, whose blocks are known by their state IDs only.
	 */
	private static final String WITHOUT_BLOCK_DATA = "26.2";
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	@Test
	void knowsTheBlockUnderThePlayerAndSeesASetblockChangeIt(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		World world = player.capability(World.class);

		server.fixture("anvil-block", player, "below");
		String[] below = server.reply(player, "anvil:below:").split(":", 4);
		int x = Integer.parseInt(below[0]);
		int y = Integer.parseInt(below[1]);
		int z = Integer.parseInt(below[2]);
		String held = below[3];

		SeenBlock known = world.awaitBlock(x, y, z, BlockQuery.any(), TIMEOUT);
		SeenDimension dimension = world.dimension().orElseThrow();
		assertEquals("minecraft:overworld", dimension.getKey());
		assertTrue(dimension.contains(y));
		assertTrue(world.isChunkLoaded(x >> 4, z >> 4));
		assertEquals(!TestedRelease.isAtLeast(WITHOUT_BLOCK_DATA), world.knowsBlockNames());
		if (world.knowsBlockNames()) assertEquals(held, known.name());
		else assertFalse(known.isNamed());

		WorldWatch blocks = world.watch();
		String placed = held.equals("minecraft:gold_block") ? "minecraft:diamond_block" : "minecraft:gold_block";
		server.console("setblock " + x + " " + y + " " + z + " " + placed);
		BlockQuery replaced = world.knowsBlockNames()
				? BlockQuery.named(placed)
				: BlockQuery.state(known.getStateId()).negate();
		SeenBlock changed = blocks.changed(x, y, z, replaced, TIMEOUT);
		assertNotEquals(known.getStateId(), changed.getStateId());
		assertEquals(changed, world.block(x, y, z).orElseThrow());

		server.console("setblock " + x + " " + y + " " + z + " " + held);
		BlockQuery restored = world.knowsBlockNames() ? BlockQuery.named(held) : BlockQuery.any();
		blocks.changed(x, y, z, restored, TIMEOUT);
	}
}
