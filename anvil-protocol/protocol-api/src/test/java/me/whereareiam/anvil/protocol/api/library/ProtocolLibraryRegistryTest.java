package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolLibraryRegistryTest {
	@Test
	void indexesSeveralLibrariesInDiscoveryOrderWithoutCreatingThem() {
		StubProvider first = new StubProvider("first");
		StubProvider second = new StubProvider("second");
		ProtocolLibraryRegistry registry = new ProtocolLibraryRegistry(List.of(first, second));

		assertEquals(List.of("first", "second"), List.copyOf(registry.ids()));
		assertEquals(List.of(first, second), List.copyOf(registry.all()));
		assertSame(second, registry.require("second"));
		assertSame(first, registry.find("first").orElseThrow());
		assertTrue(registry.find("missing").isEmpty());
	}

	@Test
	void unknownLibrariesNameTheInstalledOnes() {
		ProtocolLibraryRegistry registry = new ProtocolLibraryRegistry(List.of(new StubProvider("first"), new StubProvider("second")));

		var failure = assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));

		assertEquals("Unknown protocol library 'missing'. Installed: [first, second]", failure.getMessage());
	}

	@Test
	void rejectsBlankAndDuplicateIdentifiers() {
		assertThrows(IllegalArgumentException.class, () -> new ProtocolLibraryRegistry(List.of(new StubProvider(" "))));
		var duplicate = assertThrows(IllegalArgumentException.class,
				() -> new ProtocolLibraryRegistry(List.of(new StubProvider("same"), new StubProvider("same"))));
		assertTrue(duplicate.getMessage().contains("Duplicate protocol library ID 'same'"));
	}

	private record StubProvider(String id) implements ProtocolLibraryProvider {
		@Override
		public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
			return List.of();
		}

		@Override
		public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
			return new ProtocolLibrary() {
				public @NotNull String id() { return id; }
				public @NotNull List<ProtocolRelease> releases() { return List.of(); }
				public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) { throw new UnsupportedOperationException(); }
				public void close() { }
			};
		}
	}
}
