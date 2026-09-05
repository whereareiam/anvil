package me.whereareiam.anvil.capability.session.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Installs session lifecycle operations into an MCProtocol worker.
 */
public final class McProtocolSessionAdapter implements ProtocolCapabilityAdapter {
	@Override
	public @NotNull String id() {
		return McProtocolSessionProvider.ID;
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("session.connect", (player, ignored) -> {
			player.connect();
			return player.mapper().createObjectNode();
		});
		registry.operation("session.disconnect", (player, ignored) -> {
			player.disconnect();
			return player.mapper().createObjectNode();
		});
		registry.operation("session.rejoin", (player, ignored) -> {
			player.rejoin();
			return player.mapper().createObjectNode();
		});
	}
}
