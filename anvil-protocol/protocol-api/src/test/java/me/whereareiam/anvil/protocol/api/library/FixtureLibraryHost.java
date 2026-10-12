package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A host of two libraries, registered in the tests' service descriptors, so that discovery installs its libraries.
 */
public final class FixtureLibraryHost implements ProtocolLibraryHost {
	@Override
	public @NotNull List<ProtocolLibraryProvider> libraries(@NotNull ClassLoader loader) {
		return List.of(new Hosted("hosted-first"), new Hosted("hosted-second"));
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
