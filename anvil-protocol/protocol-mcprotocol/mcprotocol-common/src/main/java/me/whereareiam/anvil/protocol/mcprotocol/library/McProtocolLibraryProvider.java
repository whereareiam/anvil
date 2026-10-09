package me.whereareiam.anvil.protocol.mcprotocol.library;

import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.protocol.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the MCProtocolLib library: version-isolated client pools for the releases in its release data and
 * Microsoft account authentication.
 *
 * <p>Release data is read once per additional release file: listing the releases for selection and creating the
 * library share one catalog, so a notice about the user's data is printed once.</p>
 */
public final class McProtocolLibraryProvider implements ProtocolLibraryProvider {
	private final Map<Path, McProtocolReleaseCatalog> withAdditionalReleases = new ConcurrentHashMap<>();
	private @Nullable McProtocolReleaseCatalog builtIn;

	@Override
	public @NotNull String id() {
		return McProtocolClient.LIBRARY_ID;
	}

	/**
	 * Returns the built-in releases and the additional releases of {@link ProtocolLibraryContext#getAdditionalReleases()}.
	 *
	 * @param context engine-scoped inputs, optionally naming a user-supplied release file
	 * @return releases ordered by release key
	 * @throws IllegalArgumentException when the release data is invalid or the user-supplied releases conflict with it
	 */
	@Override
	public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
		return catalog(context.getAdditionalReleases()).releases();
	}

	@Override
	public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
		return new McProtocolClientPool(context.getCacheDirectory(), catalog(context.getAdditionalReleases()),
				new MicrosoftAuthentication(context.getAccountsDirectory()), context.getArtifacts());
	}

	@Override
	public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path accountsDirectory) {
		return Optional.of(new MicrosoftAuthentication(accountsDirectory));
	}

	private McProtocolReleaseCatalog catalog(@Nullable Path additionalReleases) {
		if (additionalReleases != null)
			return withAdditionalReleases.computeIfAbsent(additionalReleases, McProtocolReleaseCatalog::load);

		synchronized (this) {
			if (builtIn == null) builtIn = McProtocolReleaseCatalog.load(null);
			return builtIn;
		}
	}
}
