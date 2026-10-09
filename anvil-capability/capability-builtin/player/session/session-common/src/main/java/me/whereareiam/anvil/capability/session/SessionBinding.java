package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Installs the session lifecycle operations into the native worker of any protocol library. The operations
 * only drive the player's own lifecycle controls, so the binding never touches the native session.
 */
public final class SessionBinding implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return SessionProvider.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.empty();
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		operations.register(SessionOperations.CONNECT, ignored -> {
			player.connect();
			return null;
		});
		operations.register(SessionOperations.DISCONNECT, ignored -> {
			player.disconnect();
			return null;
		});
		operations.register(SessionOperations.REJOIN, ignored -> {
			player.rejoin();
			return null;
		});

		return () -> { };
	}
}
