package me.whereareiam.anvil.capability.packets.model;

import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordedPacketsTest {
	private static final RecordingHeader HEADER = new RecordingHeader(RecordingHeader.FORMAT, 774, "1.21.11", "Alice");

	@Test
	void keepsTheBytesAloneOfACostlyPacketAndTheValueOfEveryOther() {
		ClientboundSystemChat welcome = new ClientboundSystemChat(Text.literal("welcome"), false);
		RecordedPackets recording = new RecordedPackets(HEADER, List.of(
				new ClientboundFrame(ConnectionState.PLAY, 0x27, "map_chunk", new byte[]{1, 2},
						new ClientboundLevelChunkWithLight(new ChunkPosition(1, 2), List.of())),
				new ClientboundFrame(ConnectionState.PLAY, 0x77, "system_chat", new byte[]{3}, welcome)
		));

		ClientboundFrame chunk = recording.getFrames().get(0);
		assertEquals(Optional.empty(), chunk.packet());
		assertEquals(Optional.of("map_chunk"), chunk.name());
		assertArrayEquals(new byte[]{1, 2}, chunk.payload());
		assertEquals(Optional.of(welcome), recording.getFrames().get(1).packet());
	}
}
