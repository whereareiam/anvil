package me.whereareiam.anvil.capability.runtime;

import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies the dependency-discovered capability composer to the engine through the protocol API.
 */
public final class CapabilityPlayerComposerProvider implements ProtocolPlayerComposerProvider {
	@Override
	public @NotNull String id() {
		return "capabilities";
	}

	@Override
	public @NotNull ProtocolPlayerComposer create(@NotNull String protocolId) {
		return PlayerCapabilityRuntime.discover(protocolId);
	}
}
