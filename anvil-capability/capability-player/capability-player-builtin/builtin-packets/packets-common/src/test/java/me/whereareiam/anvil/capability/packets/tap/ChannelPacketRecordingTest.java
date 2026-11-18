package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelPacketRecordingTest {
	private static final RecordingHeader HEADER = new RecordingHeader(RecordingHeader.FORMAT, 774, "1.21.11", "Alice");

	private final List<Integer> closed = new ArrayList<>();
	private final ChannelPacketRecording recording = new ChannelPacketRecording(3, HEADER, closed::add);

	@Test
	void keepsEveryFrameInOrderUntilItStops() {
		recording.receive(frame(0x2a, 1));
		recording.receive(frame(0x08, 2));

		RecordedPackets recorded = recording.stop();
		recording.receive(frame(0x08, 3));

		assertEquals(HEADER, recorded.getHeader());
		assertEquals(List.of(new ClientboundFrame(ConnectionState.PLAY, 0x2a, "update_light", new byte[]{1}, null),
				new ClientboundFrame(ConnectionState.PLAY, 0x08, "update_light", new byte[]{2}, null)), recorded.getFrames());
		assertEquals(List.of(3), closed);
	}

	@Test
	void stopsOnce() {
		recording.stop();

		assertThrows(IllegalStateException.class, recording::stop);
		recording.close();
		assertEquals(List.of(3), closed);
	}

	@Test
	void refusesAFrameWithoutItsBytes() {
		assertThrows(NullPointerException.class, () -> recording.receive(new TappedFrame(List.of(3),
				ConnectionState.PLAY, 0x08, "block_change", null, null)));
	}

	private static TappedFrame frame(int id, int payload) {
		return new TappedFrame(List.of(3), ConnectionState.PLAY, id, "update_light", new byte[]{(byte) payload}, null);
	}
}
