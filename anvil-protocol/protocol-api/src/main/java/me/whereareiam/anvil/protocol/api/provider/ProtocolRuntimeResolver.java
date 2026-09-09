package me.whereareiam.anvil.protocol.api.provider;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;

/**
 * Supplies the exact verified native worker runtime required by a protocol provider.
 */

public interface ProtocolRuntimeResolver {
	/**
	 * Resolves a pinned runtime artifact without replacing its declared checksum.
	 * @param artifact exact artifact URI
	 * @param destination provider-owned cache destination
	 * @param sha256 required SHA-256 pin
	 * @return verified local runtime
	 */
	@NotNull Path resolve(@NotNull URI artifact, @NotNull Path destination, @NotNull String sha256);
}
