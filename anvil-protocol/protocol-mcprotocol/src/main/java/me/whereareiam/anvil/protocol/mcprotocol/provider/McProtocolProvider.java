package me.whereareiam.anvil.protocol.mcprotocol.provider;

import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
import me.whereareiam.anvil.protocol.mcprotocol.authentication.MicrosoftAuthentication;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Creates version-isolated MCProtocolLib client pools.
 */
public final class McProtocolProvider implements ProtocolProvider {
	static final String ID = "mcprotocol";

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ProtocolRuntimeResolver artifacts) {
		return new McProtocolClientPool(cacheDirectory, new MicrosoftAuthentication(cacheDirectory), artifacts);
	}

	@Override
	public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path cacheDirectory) {
		return Optional.of(new MicrosoftAuthentication(cacheDirectory));
	}
}
