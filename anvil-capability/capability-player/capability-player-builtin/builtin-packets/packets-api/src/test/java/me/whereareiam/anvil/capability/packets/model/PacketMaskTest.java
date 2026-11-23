package me.whereareiam.anvil.capability.packets.model;

import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketMaskTest {
	@Test
	void comparesAClassByValueWithoutItsIgnoredFields() {
		PacketMask mask = PacketMask.strict().ignore(ClientboundSystemChat.class, ClientboundSystemChat.Fields.text);

		assertEquals(Map.of(ClientboundSystemChat.class, Set.of(ClientboundSystemChat.Fields.text)), mask.fields());
	}

	@Test
	void refusesToCompareACostlyClassByValue() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> PacketMask.strict().ignore(ClientboundLevelChunkWithLight.class));

		assertEquals("ClientboundLevelChunkWithLight is costly to decode, so recordings keep its bytes alone and "
				+ "compare them; leave its packets out by name instead", failure.getMessage());
	}
}
