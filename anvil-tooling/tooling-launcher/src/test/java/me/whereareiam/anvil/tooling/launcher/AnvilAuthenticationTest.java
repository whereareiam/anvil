package me.whereareiam.anvil.tooling.launcher;

import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilAuthenticationTest {
	private static final Path ACCOUNTS = Path.of("accounts");

	@Test
	void selectsTheNamedLibraryOrTheSoleLibraryOfferingAuthentication() {
		Library online = new Library("online", true);
		Library other = new Library("other", true);

		assertSame(online.authentication, AnvilAuthentication.authentication(registry(online, new Library("offline", false)), null, ACCOUNTS));
		assertSame(other.authentication, AnvilAuthentication.authentication(registry(online, other), "other", ACCOUNTS));
	}

	@Test
	void refusesUnknownOfflineAndAmbiguousSelections() {
		Library first = new Library("first", true);
		Library second = new Library("second", true);
		Library offline = new Library("offline", false);

		var unknown = assertThrows(IllegalArgumentException.class,
				() -> AnvilAuthentication.authentication(registry(first), "missing", ACCOUNTS));
		var unsupported = assertThrows(IllegalStateException.class,
				() -> AnvilAuthentication.authentication(registry(offline), "offline", ACCOUNTS));
		var none = assertThrows(IllegalStateException.class,
				() -> AnvilAuthentication.authentication(registry(offline), null, ACCOUNTS));
		var ambiguous = assertThrows(IllegalStateException.class,
				() -> AnvilAuthentication.authentication(registry(first, second, offline), null, ACCOUNTS));

		assertTrue(unknown.getMessage().contains("Installed: [first]"), unknown.getMessage());
		assertTrue(unsupported.getMessage().contains("'offline' does not support interactive authentication"));
		assertTrue(none.getMessage().startsWith("No installed protocol library supports interactive authentication"));
		assertTrue(ambiguous.getMessage().contains("[first, second]"), ambiguous.getMessage());
		assertTrue(ambiguous.getMessage().contains("--library"));
	}

	private ProtocolLibraryRegistry registry(Library... libraries) {
		return new ProtocolLibraryRegistry(List.of(libraries));
	}

	private static final class Library implements ProtocolLibraryProvider {
		private final String id;
		private final ProtocolAuthentication authentication;

		private Library(String id, boolean online) {
			this.id = id;
			this.authentication = online ? new ProtocolAuthentication() {
				@Override
				public void login(@NotNull String accountId, @NotNull Consumer<String> output) { }

				@Override
				public void logout(@NotNull String accountId, @NotNull Consumer<String> output) { }
			} : null;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
			return List.of();
		}

		@Override
		public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path accountsDirectory) {
			return Optional.ofNullable(authentication);
		}
	}
}
