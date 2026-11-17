package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.capability.packets.PacketRecording;
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.channel.PacketsChannel;
import me.whereareiam.anvil.capability.packets.channel.TapRequest;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelPacketTapsTest {
	private static final ClientboundSystemChat WELCOME = new ClientboundSystemChat(Text.literal("welcome"), false);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(PacketsChannel.ID));
	private final List<TapRequest> opened = new ArrayList<>();
	private final List<Integer> closed = new ArrayList<>();
	private final ChannelPacketTaps taps = new ChannelPacketTaps(channel, "Alice", "1.21.11");

	ChannelPacketTapsTest() {
		channel.answer(PacketsChannel.TAP, request -> {
			opened.add(request);
			return 774;
		});
		channel.answer(PacketsChannel.UNTAP, id -> {
			closed.add(id);
			return null;
		});
	}

	@Test
	void opensATapByTheKindsAndNamesOfItsSelectionWithoutBytes() {
		PacketSelection selection = PacketSelection.of(ClientboundSystemChat.class).and(PacketSelection.named("update_light"));

		taps.tap(selection);

		assertEquals(List.of(new TapRequest(1, false, Set.of("ClientboundSystemChat"), Set.of("update_light"), false)),
				opened);
	}

	@Test
	void handsEachFrameToTheTapsItNames() {
		PacketTap chat = taps.tap(PacketSelection.of(ClientboundSystemChat.class));
		PacketTap every = taps.tap(PacketSelection.every());

		channel.publish(PacketsChannel.FRAMES, frame(List.of(1, 2), WELCOME));
		channel.publish(PacketsChannel.FRAMES, frame(List.of(2), null));

		assertEquals(1, chat.history().size());
		assertEquals(2, every.history().size());
	}

	@Test
	void closesATapOnTheWorkerOnceAndStopsHandingItFrames() {
		PacketTap tap = taps.tap(PacketSelection.every());

		tap.close();
		tap.close();
		channel.publish(PacketsChannel.FRAMES, frame(List.of(1), WELCOME));

		assertEquals(List.of(1), closed);
		assertTrue(tap.history().isEmpty());
	}

	@Test
	void recordsEveryFrameWithItsBytesUnderTheProtocolTheWorkerNames() {
		PacketRecording recording = taps.record();
		channel.publish(PacketsChannel.FRAMES, new TappedFrame(List.of(1), ConnectionState.PLAY, 0x77, "system_chat",
				new byte[]{1, 2}, WELCOME));

		var recorded = recording.stop();

		assertEquals(List.of(new TapRequest(1, true, Set.of(), Set.of(), true)), opened);
		assertEquals(new RecordingHeader(RecordingHeader.FORMAT, 774, "1.21.11", "Alice"), recorded.getHeader());
		assertEquals(1, recorded.getFrames().size());
		assertEquals("system_chat", recorded.getFrames().getFirst().name().orElseThrow());
		assertEquals(List.of(1), closed);
	}

	@Test
	void forgetsATapTheWorkerRefuses() {
		channel.answer(PacketsChannel.TAP, request -> {
			throw new IllegalArgumentException("Protocol 774 has no packet named nothing that a server sends");
		});

		assertThrows(IllegalArgumentException.class, () -> taps.tap(PacketSelection.named("nothing")));
		channel.publish(PacketsChannel.FRAMES, frame(List.of(1), WELCOME));
	}

	private static TappedFrame frame(List<Integer> taps, ClientboundSystemChat packet) {
		return new TappedFrame(taps, ConnectionState.PLAY, 0x77, "system_chat", null, packet);
	}
}
