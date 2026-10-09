package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Service-provider interface for one simulated-player protocol library, such as MCProtocolLib.
 *
 * <p>Implementations are discovered through {@link ServiceLoader} from
 * {@code META-INF/services/me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider}. Several
 * libraries may be installed at once; each player selects one from its declaration or from the
 * {@link ProtocolRelease#support(MinecraftVersion) support level} of the
 * releases reported here. Selection reads {@link #releases(ProtocolLibraryContext)} without creating
 * the library, so release data must be cheap and side-effect free.</p>
 *
 * <pre>{@code
 * public final class ExampleLibraryProvider implements ProtocolLibraryProvider {
 *     public String id() { return "example"; }
 *     public List<ProtocolRelease> releases(ProtocolLibraryContext context) { return ExampleReleases.all(); }
 *     public ProtocolLibrary create(ProtocolLibraryContext context) { return new ExampleLibrary(context); }
 * }
 * }</pre>
 */
public interface ProtocolLibraryProvider {
	/**
	 * Returns the stable library identifier used by {@code protocolLibrary} declarations and stored accounts.
	 *
	 * @return non-blank library identifier
	 */
	@NotNull String id();

	/**
	 * Returns every release this library can run, including additional user-supplied releases named by
	 * {@link ProtocolLibraryContext#getAdditionalReleases()}.
	 *
	 * @param context engine-scoped directories, artifact resolution, and optional additional release data
	 * @return immutable releases; each Minecraft version appears in at most one release
	 * @throws IllegalArgumentException when the release data is invalid
	 */
	@NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context);

	/**
	 * Creates the run-scoped library that owns this library's clients and worker processes.
	 * The engine creates it lazily when the first player selects this library.
	 *
	 * @param context engine-scoped directories, artifact resolution, and optional additional release data
	 * @return library instance closed by the engine
	 */
	@NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context);

	/**
	 * Provides an optional login/logout workflow without creating the library or a player.
	 * Offline-only libraries keep the empty default implementation.
	 *
	 * @param accountsDirectory local account store directory
	 * @return library-owned authentication service, when supported
	 */
	default @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path accountsDirectory) {
		return Optional.empty();
	}
}
