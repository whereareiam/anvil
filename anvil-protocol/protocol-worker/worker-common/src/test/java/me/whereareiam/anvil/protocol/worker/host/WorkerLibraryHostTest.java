package me.whereareiam.anvil.protocol.worker.host;

import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerLibraryHostTest {
	@TempDir
	Path temporary;

	@Test
	void installsEveryLibraryThatPlugsIntoTheWorkerAsAProtocolLibrary() {
		List<ProtocolLibraryProvider> libraries = new WorkerLibraryHost().libraries(WorkerLibraryHostTest.class.getClassLoader());

		assertEquals(List.of("probelib"), libraries.stream().map(ProtocolLibraryProvider::id).toList());
		assertEquals(List.of("probelib"), List.copyOf(ProtocolLibraryRegistry.discover().ids()));
	}

	@Test
	void readsTheLibrarysReleasesAndRunsItsPlayersInAWorkerPool() {
		ProtocolLibraryProvider probe = ProtocolLibraryRegistry.discover().require("probelib");

		assertEquals(List.of("1.20.4-1", "1.21.11-1"),
				probe.releases(context()).stream().map(ProtocolRelease::getLibraryVersion).toList());
		assertTrue(probe.authentication(temporary).isEmpty());
		try (ProtocolLibrary library = probe.create(context())) {
			assertEquals("probelib", library.id());
			assertEquals(probe.releases(context()), library.releases());
		}
	}

	private ProtocolLibraryContext context() {
		return ProtocolLibraryContext.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.artifacts((uri, destination, sha256) -> {
					throw new AssertionError("Reading release data must not download artifacts");
				})
				.build();
	}
}
