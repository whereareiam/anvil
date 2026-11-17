package me.whereareiam.anvil.capability.packets.java;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.packets.channel.TapRequest;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.JavaPacketData;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketsExtensionTest {
	/**
	 * The ID of {@code system_chat} in the play state of protocol 774.
	 */
	private static final int SYSTEM_CHAT = 0x77;
	/**
	 * The ID of {@code block_change} in the play state of protocol 774, which the test delivers without a value.
	 */
	private static final int BLOCK_CHANGE = 0x08;
	/**
	 * The ID of {@code map_chunk} in the play state of protocol 774.
	 */
	private static final int MAP_CHUNK = JavaPacketData.shipped().find(TestJavaPlayer.PROTOCOL).orElseThrow()
			.clientboundId(ConnectionState.PLAY, "map_chunk").orElseThrow();
	private static final ClientboundLevelChunkWithLight CHUNK = new ClientboundLevelChunkWithLight(
			new ChunkPosition(1, 2), List.of());
	private static final ClientboundSystemChat WELCOME = new ClientboundSystemChat(Text.literal("welcome"), false);

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
	private final WorkerBinding binding = new JavaPacketsExtension().bind(player, operations);

	JavaPacketsExtensionTest() {
		player.connect();
	}

	@Test
	void listensToFramesOnlyWhileATapIsOpen() {
		assertFalse(player.protocol().wantsFrames());

		assertEquals(TestJavaPlayer.PROTOCOL, operations.call(PacketsChannel.TAP, every(1, false)));
		assertTrue(player.protocol().wantsFrames());

		operations.call(PacketsChannel.UNTAP, 1);
		assertFalse(player.protocol().wantsFrames());
	}

	@Test
	void sendsTheFramesATapTakesByClassWithoutTheirBytes() {
		operations.call(PacketsChannel.TAP, new TapRequest(1, false, Set.of("ClientboundSystemChat"), Set.of(), false));

		player.deliver(ConnectionState.PLAY, BLOCK_CHANGE, new byte[]{1}, null);
		player.deliver(ConnectionState.PLAY, SYSTEM_CHAT, new byte[]{2}, WELCOME);

		TappedFrame frame = player.emitted(PacketsChannel.FRAMES).getFirst();
		assertEquals(1, player.emitted(PacketsChannel.FRAMES).size());
		assertEquals(List.of(1), frame.getTaps());
		assertEquals("system_chat", frame.getName());
		assertEquals(WELCOME, frame.getPacket());
		assertNull(frame.getPayload());
	}

	@Test
	void sendsTheFramesATapTakesByNameWhetherOrNotTheProtocolModelsThem() {
		operations.call(PacketsChannel.TAP, new TapRequest(1, false, Set.of(), Set.of("block_change"), false));

		player.deliver(ConnectionState.PLAY, BLOCK_CHANGE, new byte[]{1}, null);
		player.deliver(ConnectionState.PLAY, SYSTEM_CHAT, new byte[]{2}, WELCOME);

		List<TappedFrame> frames = player.emitted(PacketsChannel.FRAMES);
		assertEquals(1, frames.size());
		assertEquals(BLOCK_CHANGE, frames.getFirst().getId());
		assertNull(frames.getFirst().getPacket());
	}

	@Test
	void sendsAFrameOnceForEveryTapThatTakesItWithItsBytesWhenOneNeedsThem() {
		operations.call(PacketsChannel.TAP, new TapRequest(1, false, Set.of("ClientboundSystemChat"), Set.of(), false));
		operations.call(PacketsChannel.TAP, every(2, true));

		player.deliver(ConnectionState.PLAY, SYSTEM_CHAT, new byte[]{2, 3}, WELCOME);
		player.deliver(ConnectionState.PLAY, BLOCK_CHANGE, new byte[]{1}, null);

		List<TappedFrame> frames = player.emitted(PacketsChannel.FRAMES);
		assertEquals(List.of(List.of(1, 2), List.of(2)), frames.stream().map(TappedFrame::getTaps)
				.map(taps -> taps.stream().sorted().toList()).toList());
		assertArrayEquals(new byte[]{2, 3}, frames.getFirst().getPayload());
	}

	@Test
	void recordsACostlyPacketByItsBytesAloneWithoutHavingItDecoded() {
		operations.call(PacketsChannel.TAP, every(1, true));
		assertFalse(player.protocol().wants(ClientboundLevelChunkWithLight.class));

		player.deliver(ConnectionState.PLAY, MAP_CHUNK, new byte[]{4}, CHUNK);

		TappedFrame frame = player.emitted(PacketsChannel.FRAMES).getFirst();
		assertEquals("map_chunk", frame.getName());
		assertArrayEquals(new byte[]{4}, frame.getPayload());
		assertNull(frame.getPacket());
	}

	@Test
	void wantsACostlyPacketWhileATapNamesItsClassAndSendsItsValueOnlyThen() {
		operations.call(PacketsChannel.TAP, every(1, true));
		operations.call(PacketsChannel.TAP, new TapRequest(2, false, Set.of("ClientboundLevelChunkWithLight"), Set.of(),
				false));
		assertTrue(player.protocol().wants(ClientboundLevelChunkWithLight.class));

		player.deliver(ConnectionState.PLAY, MAP_CHUNK, new byte[]{4}, CHUNK);
		operations.call(PacketsChannel.UNTAP, 2);
		player.deliver(ConnectionState.PLAY, MAP_CHUNK, new byte[]{5}, CHUNK);

		List<TappedFrame> frames = player.emitted(PacketsChannel.FRAMES);
		assertEquals(CHUNK, frames.get(0).getPacket());
		assertNull(frames.get(1).getPacket());
		assertFalse(player.protocol().wants(ClientboundLevelChunkWithLight.class));
	}

	@Test
	void refusesANameTheProtocolHasNoPacketOf() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> operations.call(
				PacketsChannel.TAP, new TapRequest(1, false, Set.of(), Set.of("level_chunk_with_light"), false)));

		assertEquals("Protocol 774 has no packet named level_chunk_with_light that a server sends",
				failure.getMessage());
		assertFalse(player.protocol().wantsFrames());
	}

	@Test
	void refusesNamesForAProtocolAnvilShipsNoNamesFor() {
		TestJavaPlayer unnamed = new TestJavaPlayer("Bob", MinecraftVersion.parse("26.3"), 777, type -> true);
		TestOperations unnamedOperations = new TestOperations();
		new JavaPacketsExtension().bind(unnamed, unnamedOperations);

		assertThrows(IllegalArgumentException.class, () -> unnamedOperations.call(PacketsChannel.TAP,
				new TapRequest(1, false, Set.of(), Set.of("system_chat"), false)));
		assertEquals(777, unnamedOperations.call(PacketsChannel.TAP, every(2, true)));
	}

	@Test
	void refusesAKindTheProtocolHasNoPacketOf() {
		assertThrows(IllegalArgumentException.class, () -> operations.call(PacketsChannel.TAP,
				new TapRequest(1, false, Set.of("ClientboundTeleport"), Set.of(), false)));
	}

	@Test
	void stopsListeningWhenTheBindingCloses() {
		operations.call(PacketsChannel.TAP, every(1, true));

		binding.close();

		assertFalse(player.protocol().wantsFrames());
	}

	private static TapRequest every(int id, boolean payloads) {
		return new TapRequest(id, true, Set.of(), Set.of(), payloads);
	}
}
