package me.whereareiam.anvil.capability.packets.recording;

import me.whereareiam.anvil.capability.packets.model.PacketDifference;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketComparisonTest {
	private final PacketComparison comparison = new PacketComparison();

	@Test
	void findsNothingInTheSameStream() {
		RecordedPackets stream = recording(light(1), chat("welcome", 2), light(3));

		assertTrue(comparison.compare(stream, stream, PacketMask.strict()).isEmpty());
	}

	@Test
	void reportsAMissingFrameOnceAndKeepsComparingAfterIt() {
		List<PacketDifference> differences = comparison.compare(recording(light(1), chat("welcome", 2), light(3)),
				recording(light(1), light(3)), PacketMask.strict());

		assertEquals(List.of(new PacketDifference(1, -1, "the actual stream lacks PLAY system_chat")), differences);
	}

	@Test
	void reportsAnExtraFrame() {
		List<PacketDifference> differences = comparison.compare(recording(light(1)), recording(light(1), light(2)),
				PacketMask.strict());

		assertEquals(List.of(new PacketDifference(-1, 1, "the actual stream has an extra PLAY update_light")), differences);
	}

	@Test
	void reportsWhereTheBytesOfAPairDiffer() {
		List<PacketDifference> differences = comparison.compare(recording(light(1, 2, 3)), recording(light(1, 9)),
				PacketMask.strict());

		assertEquals(List.of(new PacketDifference(0, 0,
				"PLAY update_light: the bytes differ from offset 1 (expected 3 bytes, found 2)")), differences);
	}

	@Test
	void leavesTheNamesAMaskIgnoresOutOfBothStreamsButCountsTheirPositions() {
		RecordedPackets expected = recording(keepAlive(1), light(2));
		RecordedPackets actual = recording(light(2), keepAlive(5), keepAlive(6), chat("late", 7));

		List<PacketDifference> differences = comparison.compare(expected, actual, PacketMask.strict().ignore("keep_alive"));

		assertEquals(List.of(new PacketDifference(-1, 3, "the actual stream has an extra PLAY system_chat")), differences);
	}

	@Test
	void comparesAMaskedClassByValueWithoutTheIgnoredFields() {
		RecordedPackets expected = recording(chat("welcome", 1));
		RecordedPackets actual = recording(chat("hello", 2));
		PacketMask byValue = PacketMask.strict().ignore(ClientboundSystemChat.class);

		assertTrue(comparison.compare(expected, actual,
				PacketMask.strict().ignore(ClientboundSystemChat.class, ClientboundSystemChat.Fields.text)).isEmpty());
		assertEquals(List.of(new PacketDifference(0, 0, "PLAY system_chat: text.content.text: expected \"welcome\", "
				+ "found \"hello\"")), comparison.compare(expected, actual, byValue));
	}

	@Test
	void comparesEntitiesByCorrespondenceRatherThanByNumber() {
		RecordedPackets expected = recording(removed(1, 42), removed(2, 42, 43));
		RecordedPackets shifted = recording(removed(9, 57), removed(8, 57, 58));
		RecordedPackets swapped = recording(removed(9, 57), removed(8, 58, 57));

		assertTrue(comparison.compare(expected, shifted, PacketMask.strict()).isEmpty());
		assertEquals(List.of(new PacketDifference(1, 1, "PLAY entity_destroy: entityIds[0]: expected entity 42 is "
				+ "entity 57 of the actual stream, which names 58 here")), comparison.compare(expected, swapped,
				PacketMask.strict()));
	}

	@Test
	void refusesRecordingsOfDifferentProtocols() {
		RecordedPackets other = new RecordedPackets(new RecordingHeader(RecordingHeader.FORMAT, 775, "26.1", "Alice"),
				List.of());

		assertThrows(IllegalArgumentException.class, () -> comparison.compare(recording(), other, PacketMask.strict()));
	}

	@Test
	void refusesAMaskedFieldTheClassDoesNotHave() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> comparison.compare(
				recording(), recording(), PacketMask.strict().ignore(ClientboundSystemChat.class, "salt")));

		assertEquals("ClientboundSystemChat has no field salt; its fields are [overlay, text]", failure.getMessage());
	}

	@Test
	void namesOnlyFieldsTheRunVaryingMasksClassesHave() {
		assertTrue(comparison.compare(recording(), recording(), PacketMask.runVarying()).isEmpty());
	}

	private static RecordedPackets recording(ClientboundFrame... frames) {
		return new RecordedPackets(new RecordingHeader(RecordingHeader.FORMAT, 774, "1.21.11", "Alice"), List.of(frames));
	}

	private static ClientboundFrame light(int... payload) {
		return frame(0x2a, "update_light", null, payload);
	}

	private static ClientboundFrame keepAlive(int payload) {
		return frame(0x2b, "keep_alive", null, payload);
	}

	private static ClientboundFrame chat(String text, int payload) {
		return frame(0x77, "system_chat", new ClientboundSystemChat(Text.literal(text), false), payload);
	}

	private static ClientboundFrame removed(int payload, int... entities) {
		List<EntityId> ids = Arrays.stream(entities).mapToObj(EntityId::new).toList();
		return frame(0x46, "entity_destroy", new ClientboundRemoveEntities(ids), payload);
	}

	private static ClientboundFrame frame(int id, String name, ClientboundPacket packet, int... payload) {
		byte[] bytes = new byte[payload.length];
		for (int index = 0; index < payload.length; index++) bytes[index] = (byte) payload[index];

		return new ClientboundFrame(ConnectionState.PLAY, id, name, bytes, packet);
	}
}
