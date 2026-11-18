package me.whereareiam.anvil.edition.java.protocol.packet;

import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientboundPacketsTest {
	@Test
	void listsEveryClientboundPacketClassOfTheProtocol() throws IOException, URISyntaxException, ClassNotFoundException {
		Path root = Path.of(ClientboundPacket.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Path packets = root.resolve(ClientboundPacket.class.getPackageName().replace('.', '/'));
		Set<Class<?>> compiled = new HashSet<>();
		try (Stream<Path> files = Files.walk(packets)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
				String name = root.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", "");
				Class<?> type = Class.forName(name);
				if (!type.isInterface() && ClientboundPacket.class.isAssignableFrom(type)) compiled.add(type);
			}
		}

		assertEquals(compiled, Set.copyOf(ClientboundPackets.kinds().values()));
	}

	@Test
	void namesTheChunkColumnCostlyAndNoOtherPacket() {
		assertTrue(ClientboundPackets.isCostly(ClientboundLevelChunkWithLight.class));
		assertFalse(ClientboundPackets.isCostly(ClientboundBlockUpdate.class));
		assertFalse(ClientboundPackets.isCostly(ClientboundSystemChat.class));
	}

	@Test
	void namesEachPacketByItsSimpleName() {
		assertEquals("ClientboundBlockUpdate", ClientboundPackets.kind(ClientboundBlockUpdate.class));
		assertEquals(Optional.of(ClientboundSystemChat.class), ClientboundPackets.find("ClientboundSystemChat"));
		assertEquals(Optional.empty(), ClientboundPackets.find("ServerboundChat"));
	}

	@Test
	void refusesAClassOutsideTheCatalog() {
		assertThrows(IllegalArgumentException.class, () -> ClientboundPackets.kind(Stranger.class));
		assertEquals(Optional.empty(), ClientboundPackets.find(ServerboundChat.class.getSimpleName()));
	}

	private static final class Stranger implements ClientboundPacket {
	}
}
