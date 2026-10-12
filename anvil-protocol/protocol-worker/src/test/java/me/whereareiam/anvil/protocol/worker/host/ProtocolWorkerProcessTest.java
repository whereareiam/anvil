package me.whereareiam.anvil.protocol.worker.host;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.worker.fixture.ProbeLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolWorkerProcessTest {
	@Test
	void refusesAReleaseThatNeedsANewerJavaThanTheWorkerWouldRunOn() {
		int current = Runtime.version().feature();
		ProtocolRelease release = ProtocolRelease.builder()
				.libraryVersion("future-1")
				.minecraftVersion(MinecraftVersion.parse("1.21.11"))
				.protocolNumber(774)
				.javaVersion(current + 4)
				.build();

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new ProtocolWorkerProcess(ProbeLibrary.LIBRARY, release, List.of()));
		assertEquals("ProbeLib release future-1 requires Java " + (current + 4) + ", but its worker would run on Java " + current,
				failure.getMessage());
	}
}
