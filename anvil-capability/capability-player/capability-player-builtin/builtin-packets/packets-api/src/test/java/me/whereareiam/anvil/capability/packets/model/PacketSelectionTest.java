package me.whereareiam.anvil.capability.packets.model;

import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketSelectionTest {
	private static final ClientboundLevelChunkWithLight CHUNK = new ClientboundLevelChunkWithLight(
			new ChunkPosition(1, 2), List.of());
	private static final ClientboundSystemChat WELCOME = new ClientboundSystemChat(Text.literal("welcome"), false);

	@Test
	void keepsTheValueOfEveryPacketThatIsNotCostly() {
		assertTrue(PacketSelection.every().keepsValue(WELCOME));
		assertTrue(PacketSelection.named("system_chat").keepsValue(WELCOME));
	}

	@Test
	void keepsTheValueOfACostlyPacketOnlyWhenItSelectsItsClass() {
		assertFalse(PacketSelection.every().keepsValue(CHUNK));
		assertFalse(PacketSelection.named("map_chunk").keepsValue(CHUNK));
		assertTrue(PacketSelection.of(ClientboundLevelChunkWithLight.class).and(PacketSelection.every()).keepsValue(CHUNK));
	}
}
