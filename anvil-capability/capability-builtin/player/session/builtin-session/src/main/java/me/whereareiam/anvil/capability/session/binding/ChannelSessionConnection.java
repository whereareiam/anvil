package me.whereareiam.anvil.capability.session.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.capability.session.SessionConnection;
import me.whereareiam.anvil.capability.session.SessionOperations;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Maps the scoped typed channel to the session implementation's own connection contract.
 */
@RequiredArgsConstructor
final class ChannelSessionConnection implements SessionConnection {
	private final @NotNull ProtocolPlayerCapabilityContext context;

	public void connect() { context.channel().request(SessionOperations.CONNECT, null); }
	public void disconnect() { context.channel().request(SessionOperations.DISCONNECT, null); }
	public void rejoin() { context.channel().request(SessionOperations.REJOIN, null); }
	public void observe(@NotNull Consumer<SessionState> observer) {
		context.channel().subscribe(PlayerConnectionEvent.CHANGED, state -> observer.accept(
				SessionState.builder().connected(state.isConnected()).kickReason(state.getReason()).build()));
	}
	public void destroyed(@NotNull Runnable observer) {
		context.channel().subscribe(PlayerConnectionEvent.DESTROYED, ignored -> observer.run());
	}

	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		context.channel().await(condition, description, timeout);
	}
}
