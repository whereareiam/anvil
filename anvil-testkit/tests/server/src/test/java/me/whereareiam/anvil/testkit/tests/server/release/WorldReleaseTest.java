package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.world.World;
import me.whereareiam.anvil.capability.world.block.BlockQuery;
import me.whereareiam.anvil.capability.world.block.BlockView;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Reads the blocks the player's client knows: a block of a chunk the server never sent it is unknown, and becomes
 * known once the player goes there and the server sends its chunk.
 */
@Release
class WorldReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final BlockPosition FAR = new BlockPosition(20_000, 100, 20_000);

	@Test
	void knowsABlockOfAChunkItLackedOnceTheServerSendsIt(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		BlockView blocks = player.capability(World.class).blocks();
		String at = FAR.getX() + " " + FAR.getY() + " " + FAR.getZ();

		assertEquals(Optional.empty(), blocks.block(FAR));
		assertFalse(blocks.isLoaded(ChunkPosition.containing(FAR)));

		server.console("forceload add " + FAR.getX() + " " + FAR.getZ());
		try {
			server.console("setblock " + at + " minecraft:gold_block");
			server.console("tp " + player.name() + " " + FAR.getX() + " " + (FAR.getY() + 1) + " " + FAR.getZ());

			Block gold = blocks.until(FAR, BlockQuery.named("gold_block"), TIMEOUT);

			assertEquals(FAR, gold.getPosition());
		} finally {
			server.console("setblock " + at + " minecraft:air");
			server.console("forceload remove " + FAR.getX() + " " + FAR.getZ());
		}
	}
}
