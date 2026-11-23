package me.whereareiam.anvil.player.library.mcprotocol.library;

import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.player.api.library.ProtocolLibrary;
import me.whereareiam.anvil.player.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.player.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.player.api.model.ProtocolRelease;
import me.whereareiam.anvil.player.library.mcprotocol.authentication.MicrosoftAuthentication;
import me.whereareiam.anvil.player.library.mcprotocol.catalog.McProtocolReleaseCatalog;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
	 * Every release's players speak the Java Edition protocol, which the release's client segment translates.
	 *
	 * @return the Java Edition protocol
	 */
	@Override
	public @NotNull Set<Class<?>> protocols() {
		return Set.of(JavaProtocol.class);
	}

	/**
	 * Returns the built-in releases and the additional releases of {@link ProtocolLibraryContext#additionalReleases()}.
	 *
	 * @param context engine-scoped inputs, optionally naming a user-supplied release file
	 * @return releases ordered by release key
	 * @throws IllegalArgumentException when the release data is invalid or the user-supplied releases conflict with it
	 */
	@Override
	public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
		return catalog(context.additionalReleases()).releases();
	}

	@Override
	public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
		return new McProtocolClientPool(context.cacheDirectory(), catalog(context.additionalReleases()),
				new MicrosoftAuthentication(context.accountsDirectory()), context.artifacts(), context.workers());
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
