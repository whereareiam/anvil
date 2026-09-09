package me.whereareiam.anvil.capability.session.binding;

import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.session.SessionOperations;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

/**
 * Installs session lifecycle operations into an MCProtocol worker.
 */
public final class McProtocolSessionBinding implements WorkerExtension<ClientSession> {
	@Override
	public @NotNull String id() {
		return McProtocolSessionProvider.ID;
	}

	@Override
	public @NotNull String backendId() { return "mcprotocol"; }

	@Override
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry registry) {
		registry.register(SessionOperations.CONNECT, ignored -> {
			player.connect();
			return null;
		});
		registry.register(SessionOperations.DISCONNECT, ignored -> {
			player.disconnect();
			return null;
		});
		registry.register(SessionOperations.REJOIN, ignored -> {
			player.rejoin();
			return null;
		});
		return () -> { };
	}
}
