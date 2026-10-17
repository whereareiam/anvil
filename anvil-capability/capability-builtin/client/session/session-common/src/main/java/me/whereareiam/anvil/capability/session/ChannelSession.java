package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session capability driven over a player's typed channel: lifecycle requests go to the worker's session
 * binding, and the connection events every worker emits keep the observed state. The channel releases the
 * event subscriptions together with the player.
 */
final class ChannelSession implements Session {
	private final CapabilityChannel channel;
	private final AtomicBoolean connected = new AtomicBoolean();
	private volatile @Nullable String kickReason;
	private volatile @Nullable DisconnectCause disconnectCause;

	ChannelSession(@NotNull CapabilityChannel channel) {
		this.channel = channel;
		channel.subscribe(PlayerConnectionEvent.CHANGED, event -> {
			connected.set(event.isConnected());
			if (event.isConnected()) return;

			disconnectCause = event.getCause();
			kickReason = event.getReason();
		});
		channel.subscribe(PlayerConnectionEvent.DESTROYED, ignored -> connected.set(false));
	}

	@Override
	public @NotNull SessionState state() {
		return SessionState.builder().connected(connected.get()).kickReason(kickReason).disconnectCause(disconnectCause).build();
	}

	@Override
	public void connect() {
		connected.set(false);
		kickReason = null;
		disconnectCause = null;
		channel.request(SessionOperations.CONNECT, null);
	}

	@Override
	public void disconnect() {
		channel.request(SessionOperations.DISCONNECT, null);
	}

	@Override
	public void rejoin() {
		connected.set(false);
		kickReason = null;
		disconnectCause = null;
		channel.request(SessionOperations.REJOIN, null);
	}

	@Override
	public void connected(@NotNull Duration timeout) {
		channel.await(connected::get, "connect", timeout);
	}

	@Override
	public void disconnected(@NotNull Duration timeout) {
		channel.await(() -> !connected.get(), "disconnect", timeout);
	}

	@Override
	public @NotNull String kicked(@NotNull Duration timeout) {
		channel.await(() -> kickReason != null, "be kicked", timeout);
		return kickReason;
	}
}
