package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.model.WorkerLaunch;
import me.whereareiam.anvil.protocol.mcprotocol.worker.child.McProtocolWorkerMain;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolWorkerProcessTest {
	@Test
	void asksItsLauncherForTheReleasesJavaAndTheWorkersMainClass() {
		ProtocolRelease release = ProtocolRelease.builder()
				.libraryVersion("future-1")
				.minecraftVersion(MinecraftVersion.parse("1.21.11"))
				.protocolNumber(774)
				.javaVersion(29)
				.build();
		AtomicReference<WorkerLaunch> requested = new AtomicReference<>();

		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new ProtocolWorkerProcess(release, List.of(),
				launch -> {
					requested.set(launch);
					throw new IOException("no Java 29 here");
				}));

		assertTrue(failure.getMessage().startsWith("Could not start protocol worker for MCProtocolLib future-1"), failure.getMessage());
		assertEquals(29, requested.get().getJavaVersion());
		assertEquals(McProtocolWorkerMain.class.getName(), requested.get().getMainClass());
		assertEquals(List.of("1.21.11", "774"), requested.get().getArguments());
	}
}
