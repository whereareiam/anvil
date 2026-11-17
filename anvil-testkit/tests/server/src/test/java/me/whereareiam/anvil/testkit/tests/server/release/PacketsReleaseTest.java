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
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taps and records the packets the server sends the player: the console places a block above the player and removes
 * it again, which every release sends as a block update.
 */
@Release
class PacketsReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@Test
	void tapsTheBlockUpdatesOfABlockPlacedAndRemovedInOrder(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Packets packets = player.capability(Packets.class);

		try (PacketTap tap = packets.tap(PacketSelection.of(ClientboundBlockUpdate.class))) {
			server.console("execute at " + player.name() + " run setblock ~ ~3 ~ minecraft:glass");
			server.console("execute at " + player.name() + " run setblock ~ ~3 ~ minecraft:air");

			ClientboundBlockUpdate placed = tap.received(ClientboundBlockUpdate.class,
					update -> update.getChange().getBlockState() != 0, TIMEOUT);
			ClientboundBlockUpdate removed = tap.received(ClientboundBlockUpdate.class,
					update -> update.getChange().getPosition().equals(placed.getChange().getPosition()), TIMEOUT);
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
			server.console("execute at " + player.name() + " run setblock ~ ~3 ~ minecraft:glass");
			tap.received(ClientboundBlockUpdate.class, update -> update.getChange().getBlockState() != 0, TIMEOUT);
			recorded = recording.stop();
			assertSequenced(tap.history());
		}

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

	private static void assertSequenced(List<TappedPacket> history) {
		for (int index = 0; index < history.size(); index++)
			assertEquals(index, history.get(index).getSequence());
	}
}
