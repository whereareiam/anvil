package me.whereareiam.anvil.player.library.mcprotocol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolWriterTest {
	@Test
	void writesVariableLengthIntegersSevenBitsAByte() {
		assertArrayEquals(new byte[]{0x7F}, new ProtocolWriter().varInt(127).toByteArray());
		assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, new ProtocolWriter().varInt(128).toByteArray());
	}

	@Test
	void readsBackWhatItWrote() {
		ProtocolReader reader = new ProtocolReader(new ProtocolWriter().varInt(300).string("ä:b").bool(true).toByteArray());

		assertEquals(300, reader.varInt());
		assertEquals("ä:b", reader.string());
		assertTrue(reader.bool());
	}
}
