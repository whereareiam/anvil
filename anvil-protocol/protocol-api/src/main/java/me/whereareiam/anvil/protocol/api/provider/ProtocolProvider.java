package me.whereareiam.anvil.protocol.api.provider;

import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Service-provider interface for a simulated-player implementation.
 */
public interface ProtocolProvider {
	/**
	 * Returns the stable protocol-backend identifier.
	 *
	 * @return backend identifier
	 */
	@NotNull String id();

	/**
	 * Creates a protocol backend using the supplied private cache root.
	 *
	 * @param cacheDirectory Anvil cache directory
	 * @param artifacts shared verified artifact cache
	 * @return backend instance
	 */
	@NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ProtocolRuntimeResolver artifacts);

	/**
	 * Provides an optional login/logout workflow without creating a protocol backend or player.
	 * Offline-only providers keep the empty default implementation.
	 *
	 * @param cacheDirectory private Anvil cache root
	 * @return provider-owned authentication service, when supported
	 */
	default @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path cacheDirectory) {
		return Optional.empty();
	}
}
