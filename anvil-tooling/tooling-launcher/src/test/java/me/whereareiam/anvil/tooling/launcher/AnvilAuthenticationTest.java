package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnvilAuthenticationTest {
	@Test
	void refusesToSignInWhenNoInstalledLibraryPlaysOnline() {
		ProtocolLibraryRegistry offline = new ProtocolLibraryRegistry(List.of(new OfflineLibrary()));

		var failure = assertThrows(IllegalStateException.class, () -> AnvilAuthentication.authentication(offline, Path.of("accounts")));

		assertEquals("No installed protocol library signs in with Microsoft accounts. Installed: [offline]", failure.getMessage());
	}

	private static final class OfflineLibrary implements ProtocolLibraryProvider {
		@Override
		public @NotNull String id() {
			return "offline";
		}

		@Override
		public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
			return List.of();
		}

		@Override
		public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
			throw new UnsupportedOperationException();
		}
	}
}
