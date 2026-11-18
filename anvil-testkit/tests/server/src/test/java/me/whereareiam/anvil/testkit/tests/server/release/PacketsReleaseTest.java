package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.packets.PacketRecording;
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.Packets;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.TappedPacket;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taps and records the packets the server sends the player: the console moves the player high above a far column, whose
 * chunk a tap waits for, places a block below it and removes it again, which every release sends as a block update among the many the world's own activity sends.
 * A recording made meanwhile keeps the chunk columns as their bytes alone, while the chunk tap gets their values.
 */
@Release
class PacketsReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	/**
	 * The height of the placed block, high above the terrain of every release's world and below its build limit.
	 */
	private static final int HEIGHT = 200;
	/**
	 * The coordinate, east and south, of the column the player moves above, which no chunk it joined with holds.
	 */
	private static final int FAR = 1600;

	@Test
	void tapsTheBlockUpdatesOfABlockPlacedAndRemovedInOrder(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Packets packets = player.capability(Packets.class);

		BlockPosition block = loadedBlock(server, player);
		try (PacketTap tap = packets.tap(PacketSelection.of(ClientboundBlockUpdate.class))) {
			server.console(setblock(block, "minecraft:glass"));
			ClientboundBlockUpdate placed = tap.received(ClientboundBlockUpdate.class, at(block), TIMEOUT);
			server.console(setblock(block, "minecraft:air"));
			ClientboundBlockUpdate removed = tap.received(ClientboundBlockUpdate.class, at(block), TIMEOUT);

			assertNotEquals(0, placed.getChange().getBlockState());
			assertEquals(0, removed.getChange().getBlockState());
		}
	}

	@Test
	void recordsEveryPacketWithItsBytesAndReadsBackTheSameStream(ScenarioContext anvil, @TempDir Path directory)
			throws Exception {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Packets packets = player.capability(Packets.class);

		RecordedPackets recorded;
		try (PacketRecording recording = packets.record(); PacketTap tap = packets.tap(PacketSelection.every())) {
			BlockPosition block = loadedBlock(server, player);
			server.console(setblock(block, "minecraft:glass"));
			tap.received(ClientboundBlockUpdate.class, at(block), TIMEOUT);
			recorded = recording.stop();
			assertSequenced(tap.history());
			assertTrue(tap.history().stream().noneMatch(packet -> packet.packet(ClientboundLevelChunkWithLight.class)
					.isPresent()), "A tap of every packet received a chunk's value");
		}

		assertTrue(recorded.getFrames().stream().noneMatch(frame -> frame.packet()
						.filter(ClientboundLevelChunkWithLight.class::isInstance).isPresent()),
				"The recording kept a chunk's value, though a chunk tap was open");

		List<ClientboundFrame> updates = recorded.getFrames().stream()
				.filter(frame -> frame.packet().filter(ClientboundBlockUpdate.class::isInstance).isPresent())
				.toList();
		assertTrue(updates.stream().allMatch(frame -> frame.size() > 0), "A recorded frame has no bytes");
		assertEquals(TestedRelease.version().toString(), recorded.getHeader().getMinecraft());

		Path file = directory.resolve("recording.packets.jsonl.gz");
		packets.write(recorded, file);
		RecordedPackets read = packets.read(file);
		assertEquals(recorded, read);
		assertTrue(packets.compare(recorded, read, PacketMask.strict()).isEmpty());
	}

	/**
	 * Moves the player above a column far from where it joined and waits for the chunk of that column, which a tap
	 * sees arrive, so the server sends the client the updates of a block in it, which lies below the player.
	 */
	private static BlockPosition loadedBlock(FixtureServer server, SimulatedPlayer player) {
		Packets packets = player.capability(Packets.class);
		try (PacketTap chunks = packets.tap(PacketSelection.of(ClientboundLevelChunkWithLight.class))) {
			server.console("tp " + player.name() + " " + FAR + " " + HEIGHT + " " + FAR);
			chunks.received(ClientboundLevelChunkWithLight.class, chunk -> chunk.getPosition().getX() == FAR / 16
					&& chunk.getPosition().getZ() == FAR / 16, TIMEOUT);
		}

		return BlockPosition.builder().x(FAR + 1).y(HEIGHT - 10).z(FAR + 1).build();
	}

	private static String setblock(BlockPosition block, String state) {
		return "setblock " + block.getX() + " " + block.getY() + " " + block.getZ() + " " + state;
	}

	private static Predicate<ClientboundBlockUpdate> at(BlockPosition block) {
		return update -> update.getChange().getPosition().equals(block);
	}

	private static void assertSequenced(List<TappedPacket> history) {
		for (int index = 0; index < history.size(); index++)
			assertEquals(index, history.get(index).getSequence());
	}
}
