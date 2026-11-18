package me.whereareiam.anvil.capability.packets.recording;

import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingFilesTest {
	private static final RecordingHeader HEADER = new RecordingHeader(RecordingHeader.FORMAT, 774, "1.21.11", "Alice");

	private final RecordingFiles files = new RecordingFiles();

	@TempDir
	Path directory;

	@Test
	void readsBackWhatItWrote() throws IOException {
		RecordedPackets recording = new RecordedPackets(HEADER, List.of(
				new ClientboundFrame(ConnectionState.PLAY, 0x77, "system_chat", new byte[]{1, 2, 3},
						new ClientboundSystemChat(Text.literal("welcome"), false)),
				new ClientboundFrame(ConnectionState.PLAY, 0x2a, "update_light", new byte[]{4}, null),
				new ClientboundFrame(ConnectionState.PLAY, 0x46, "entity_destroy", new byte[]{5},
						new ClientboundRemoveEntities(List.of(new EntityId(42))))
		));
		Path file = directory.resolve("recording.packets.jsonl.gz");

		files.write(recording, file);

		assertEquals(recording, files.read(file));
	}

	@Test
	void writesAHeaderLineAndOneLinePerFrame() throws IOException {
		Path file = directory.resolve("recording.packets.jsonl.gz");
		files.write(new RecordedPackets(HEADER, List.of(
				new ClientboundFrame(ConnectionState.PLAY, 0x2a, "update_light", new byte[]{4}, null))), file);

		List<String> lines;
		try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		}

		assertEquals("{\"format\":1,\"protocol\":774,\"minecraft\":\"1.21.11\",\"player\":\"Alice\"}", lines.get(0));
		assertEquals("{\"seq\":0,\"state\":\"PLAY\",\"id\":42,\"name\":\"update_light\",\"payload\":\"BA==\"}", lines.get(1));
	}

	@Test
	void refusesARecordingOfAnotherFormat() throws IOException {
		Path file = gzip("{\"format\":2,\"protocol\":774,\"minecraft\":\"1.21.11\",\"player\":\"Alice\"}\n");

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> files.read(file));

		assertTrue(failure.getMessage().contains("format 2"), failure.getMessage());
	}

	@Test
	void refusesAFrameOutOfPlace() throws IOException {
		Path file = gzip("{\"format\":1,\"protocol\":774,\"minecraft\":\"1.21.11\",\"player\":\"Alice\"}\n"
				+ "{\"seq\":1,\"state\":\"PLAY\",\"id\":42,\"name\":\"update_light\",\"payload\":\"BA==\"}\n");

		assertThrows(IllegalArgumentException.class, () -> files.read(file));
	}

	@Test
	void refusesAnEmptyFile() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> files.read(gzip("")));
	}

	private Path gzip(String text) throws IOException {
		Path file = Files.createTempFile(directory, "recording", ".gz");
		try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}

		return file;
	}
}
