package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
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
	 * Anvil cache root; the library owns its own subdirectory.
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
	 * User-supplied release data file in the library's own format, or null when none was configured.
	 * Releases read from it are {@link ProtocolRelease#isAdditional() additional}.
	 */
	@Nullable Path additionalReleases;
}
