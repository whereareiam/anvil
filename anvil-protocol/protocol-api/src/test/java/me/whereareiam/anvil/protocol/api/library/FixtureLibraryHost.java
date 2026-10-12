package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A host of two libraries with an account workflow they share, registered in the tests' service descriptors, so that
 * discovery installs its libraries.
 */
public final class FixtureLibraryHost implements ProtocolLibraryHost {
	@Override
	public @NotNull List<ProtocolLibraryProvider> libraries(@NotNull ClassLoader loader) {
		return List.of(new Hosted("hosted-first"), new Hosted("hosted-second"));
	}

	@Override
	public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull ClassLoader loader, @NotNull Path accountsDirectory) {
		return Optional.of(new Accounts(accountsDirectory));
	}

	/**
	 * Accounts of the directory they were offered for, without any stored.
	 */
	record Accounts(Path directory) implements ProtocolAuthentication {
		@Override
		public @NotNull Collection<AuthenticationAccount> accounts() {
			return List.of();
		}

		@Override
		public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
			throw new UnsupportedOperationException();
		}
	}

	private record Hosted(String id) implements ProtocolLibraryProvider {
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
