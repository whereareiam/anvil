package me.whereareiam.anvil.player.library.mcprotocol.client.neoforge;

import me.whereareiam.anvil.api.model.player.PayloadChannel;
import me.whereareiam.anvil.api.type.network.ChannelFlow;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import me.whereareiam.anvil.player.library.mcprotocol.client.ProtocolReader;
import me.whereareiam.anvil.player.library.mcprotocol.client.ProtocolWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeoForgeHandshakeTest {
	private static final PayloadChannel SCHEMATIC = PayloadChannel.builder()
			.id("create:place_schematic").version("6.0.10").phase(ChannelPhase.PLAY).flow(ChannelFlow.SERVERBOUND).build();
	private static final PayloadChannel REGISTRIES = PayloadChannel.builder()
			.id("neoforge:frozen_registry_sync_completed").version("1").phase(ChannelPhase.CONFIGURATION).optional(true).build();
	private final NeoForgeHandshake handshake = new NeoForgeHandshake(List.of(SCHEMATIC, REGISTRIES));

	@Test
	void claimsItsChannelsByPhaseWhenTheServerAsks() {
		NeoForgeHandshake.Answer answer = handshake.answer(NeoForgeHandshake.QUERY, new byte[]{0}).orElseThrow();
		ProtocolReader claimed = new ProtocolReader(answer.payload());

		assertEquals(NeoForgeHandshake.QUERY, answer.channel());
		assertEquals(2, claimed.varInt());
		assertEquals(4, claimed.varInt(), "the configuration phase's ordinal");
		assertEquals(1, claimed.varInt());
		assertEquals("neoforge:frozen_registry_sync_completed", claimed.string());
		assertEquals("1", claimed.string());
		assertFalse(claimed.bool(), "both directions");
		assertTrue(claimed.bool(), "optional");
		assertEquals(1, claimed.varInt(), "the play phase's ordinal");
		assertEquals(1, claimed.varInt());
		assertEquals("create:place_schematic", claimed.string());
		assertEquals("6.0.10", claimed.string());
		assertTrue(claimed.bool());
		assertEquals(0, claimed.varInt(), "serverbound");
		assertFalse(claimed.bool());
	}

	@Test
	void acknowledgesTheSyncedRegistriesFlagsAndEnums() {
		assertEquals("neoforge:frozen_registry_sync_completed",
				handshake.answer("neoforge:frozen_registry_sync_completed", new byte[0]).orElseThrow().channel());
		assertEquals("neoforge:feature_flags_ack", handshake.answer("neoforge:feature_flags", new byte[]{0}).orElseThrow().channel());
		assertEquals("neoforge:extensible_enum_ack", handshake.answer("neoforge:extensible_enum_data", new byte[]{0}).orElseThrow().channel());
	}

	@Test
	void knowsEveryDataMapTheServerSyncs() {
		byte[] maps = new ProtocolWriter().varInt(1).string("minecraft:item").varInt(2)
				.string("neoforge:furnace_fuels").bool(true).string("neoforge:compostables").bool(false).toByteArray();
		byte[] known = new ProtocolWriter().varInt(1).string("minecraft:item").varInt(2)
				.string("neoforge:furnace_fuels").string("neoforge:compostables").toByteArray();

		NeoForgeHandshake.Answer answer = handshake.answer("neoforge:known_registry_data_maps", maps).orElseThrow();

		assertEquals("neoforge:known_registry_data_maps_reply", answer.channel());
		assertArrayEquals(known, answer.payload());
	}

	@Test
	void leavesTheModsPayloadsUnanswered() {
		assertTrue(handshake.answer("create:configure_schematicannon", new byte[]{1, 2}).isEmpty());
	}
}
