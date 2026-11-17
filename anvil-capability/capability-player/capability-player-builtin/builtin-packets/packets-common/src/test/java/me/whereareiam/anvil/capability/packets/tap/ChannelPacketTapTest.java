package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.TappedPacket;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelPacketTapTest {
	private static final Duration SHORT = Duration.ofMillis(50);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(PacketsChannel.ID));
	private final List<Integer> closed = new ArrayList<>();
	private final ChannelPacketTap tap = new ChannelPacketTap(1,
			PacketSelection.of(ClientboundSystemChat.class).and(PacketSelection.named("update_light")), channel,
			closed::add);

	@Test
	void waitsForAMatchingPacketAndMovesPastIt() {
		tap.receive(chat("first"));
		tap.receive(chat("second"));

		assertEquals("first", tap.received(ClientboundSystemChat.class, chat -> true).getText().plain());
		assertEquals("second", tap.received(ClientboundSystemChat.class, chat -> true).getText().plain());
		assertTrue(tap.unread().isEmpty());
		assertEquals(2, tap.history().size());
	}

	@Test
	void waitsForAPacketThatArrivesWhileItWaits() {
		channel.meanwhile(() -> tap.receive(chat("late")));

		assertEquals("late", tap.received(ClientboundSystemChat.class, chat -> chat.getText().plain().equals("late"))
				.getText().plain());
	}

	@Test
	void waitsForAPacketByNameThatTheProtocolDoesNotModel() {
		tap.receive(new TappedFrame(List.of(1), ConnectionState.PLAY, 0x2a, "update_light", null, null));

		TappedPacket light = tap.received("update_light");

		assertEquals(0, light.getSequence());
		assertTrue(light.packet().isEmpty());
	}

	@Test
	void keepsAChunkValueOnlyInATapThatSelectsChunks() {
		ClientboundLevelChunkWithLight chunk = new ClientboundLevelChunkWithLight(new ChunkPosition(1, 2), List.of());
		TappedFrame frame = new TappedFrame(List.of(2, 3), ConnectionState.PLAY, 0x27, "map_chunk", null, chunk);
		ChannelPacketTap every = new ChannelPacketTap(2, PacketSelection.every(), channel, closed::add);
		ChannelPacketTap chunks = new ChannelPacketTap(3, PacketSelection.of(ClientboundLevelChunkWithLight.class),
				channel, closed::add);

		every.receive(frame);
		chunks.receive(frame);

		assertTrue(every.history().getFirst().packet().isEmpty());
		assertEquals(chunk, chunks.received(ClientboundLevelChunkWithLight.class, received -> true));
	}

	@Test
	void numbersPacketsInTheOrderTheyArrived() {
		tap.receive(chat("a"));
		tap.receive(new TappedFrame(List.of(1), ConnectionState.PLAY, 0x2a, "update_light", null, null));

		assertEquals(List.of(0L, 1L), tap.history().stream().map(TappedPacket::getSequence).toList());
	}

	@Test
	void failsWhenNoMatchingPacketArrivesInTime() {
		tap.receive(chat("other"));

		assertThrows(IllegalStateException.class, () -> tap.received(ClientboundSystemChat.class,
				chat -> chat.getText().plain().equals("wanted"), SHORT));
	}

	@Test
	void failsWhenAPacketItMustNotReceiveArrives() {
		tap.neverReceived(ClientboundSystemChat.class, chat -> true, SHORT);
		tap.receive(chat("unwanted"));

		assertThrows(IllegalStateException.class, () -> tap.neverReceived(ClientboundSystemChat.class, chat -> true,
				SHORT));
	}

	@Test
	void refusesWaitsForWhatItsSelectionDoesNotTake() {
		assertThrows(IllegalArgumentException.class, () -> tap.received(ClientboundBlockUpdate.class, update -> true));
		assertThrows(IllegalArgumentException.class, () -> tap.received("map_chunk"));
	}

	@Test
	void meetsAnExpectationOnceAMatchingPacketArrived() {
		var expectation = tap.expectReceived(ClientboundSystemChat.class, chat -> true);

		tap.receive(chat("now"));

		assertTrue(expectation.met());
	}

	@Test
	void closesOnce() {
		tap.close();
		tap.close();

		assertEquals(List.of(1), closed);
	}

	private static TappedFrame chat(String text) {
		return new TappedFrame(List.of(1), ConnectionState.PLAY, 0x77, "system_chat", null,
				new ClientboundSystemChat(Text.literal(text), false));
	}
}
