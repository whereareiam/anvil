package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolWorkerLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Engine-scoped inputs supplied to one {@link ProtocolLibraryProvider}.
 */
@Value
@Builder
public class ProtocolLibraryContext {
	/**
	 * Cache root where the scenario's processes run; the library owns its own subdirectory, and what its
	 * {@link #artifacts} resolve below it lies there too.
	 */
	@NotNull Path cacheDirectory;
	/**
	 * Local account store directory shared by every library.
	 */
	@NotNull Path accountsDirectory;
	/**
	 * Verified artifact resolution shared by the engine.
	 */
	@NotNull ProtocolArtifactResolver artifacts;

	/**
	 * Starts the library's worker JVMs where the scenario's processes run.
	 */
	@NotNull ProtocolWorkerLauncher workers;
	/**
	 * User-supplied release data file in the library's own format, or null when none was configured.
	 * Releases read from it are {@link ProtocolRelease#isAdditional() additional}.
	 */
	@Nullable Path additionalReleases;
}
