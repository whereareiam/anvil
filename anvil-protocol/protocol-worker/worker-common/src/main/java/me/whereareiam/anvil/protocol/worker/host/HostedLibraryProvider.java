package me.whereareiam.anvil.protocol.worker.host;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.worker.WorkerLibraryProvider;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.account.HostedAccounts;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs one library the worker hosts as a protocol library: its releases come from its release data, its players
 * run in a {@link ProtocolWorkerPool}, and its players that use a stored account sign in with the session the library
 * derives from it.
 *
 * <p>Release data is read once per additional release file: listing the releases for selection and creating the
 * library share one catalog, so a notice about the user's data is printed once.</p>
 */
@RequiredArgsConstructor
final class HostedLibraryProvider implements ProtocolLibraryProvider {
	private final @NotNull WorkerLibraryProvider provider;

	private final Map<Path, ReleaseCatalog> withAdditionalReleases = new ConcurrentHashMap<>();
	private @Nullable ReleaseCatalog builtIn;

	@Override
	public @NotNull String id() {
		return provider.library().getId();
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
		return new ProtocolWorkerPool(
				provider.library(),
				context.getCacheDirectory(),
				catalog(context.getAdditionalReleases()),
				accountLibrary().map(library -> new HostedAccounts(List.of(library), context.getAccountsDirectory())).orElse(null),
				context.getArtifacts()
		);
	}

	/**
	 * Describes how the library derives sessions from stored accounts, when it plays online.
	 *
	 * @return the library as the shared accounts know it, or empty for an offline-only library
	 */
	@NotNull Optional<HostedAccounts.Library> accountLibrary() {
		return provider.authentication()
				.map(authentication -> new HostedAccounts.Library(provider.library(), authentication, this::newestVersion));
	}

	private MinecraftVersion newestVersion() {
		return catalog(null).releases().stream()
				.flatMap(release -> release.getMinecraftVersions().stream())
				.max(Comparator.naturalOrder())
				.orElseThrow(() -> new IllegalStateException(provider.library().getName() + " lists no release"));
	}

	private ReleaseCatalog catalog(@Nullable Path additionalReleases) {
		WorkerLibrary library = provider.library();
		if (additionalReleases != null)
			return withAdditionalReleases.computeIfAbsent(additionalReleases, path -> ReleaseCatalog.load(library, path));

		synchronized (this) {
			if (builtIn == null) builtIn = ReleaseCatalog.load(library, null);
			return builtIn;
		}
	}
}
