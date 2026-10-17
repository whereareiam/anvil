package me.whereareiam.anvil.protocol.api.library;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;

/**
 * Supplies exact, checksum-verified artifacts required by a protocol library, such as its worker runtime.
 */
public interface ProtocolArtifactResolver {
	/**
	 * Resolves a pinned artifact without replacing its declared checksum.
	 *
	 * @param artifact exact artifact URI
	 * @param destination library-owned cache destination
	 * @param sha256 required SHA-256 pin
	 * @return verified local artifact
	 */
	@NotNull Path resolve(@NotNull URI artifact, @NotNull Path destination, @NotNull String sha256);
}
