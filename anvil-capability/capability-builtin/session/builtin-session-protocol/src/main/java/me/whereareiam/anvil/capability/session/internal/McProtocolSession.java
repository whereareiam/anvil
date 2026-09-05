package me.whereareiam.anvil.capability.session.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session capability driven by namespaced MCProtocol worker operations and events.
 */
final class McProtocolSession implements Session {
	private final ProtocolPlayerConnection connection;
	private final AtomicBoolean connected = new AtomicBoolean();
	private volatile String kickReason;

	McProtocolSession(ProtocolPlayerConnection connection) {
		this.connection = connection;
		connection.subscribe("session.connected", ignored -> connected.set(true));
		connection.subscribe("session.disconnected", payload -> {
			connected.set(false);
			kickReason = payload.path("reason").asText(null);
		});
		connection.subscribe("player.destroyed", ignored -> connected.set(false));
	}

	@Override
	public @NotNull SessionState state() {
		return SessionState.builder().connected(connected.get()).kickReason(kickReason).build();
	}

	@Override
	public void connect() {
		connected.set(false);
		kickReason = null;
		connection.execute("session.connect", ignored -> { });
	}

	@Override
	public void disconnect() {
		connection.execute("session.disconnect", ignored -> { });
	}

	@Override
	public void rejoin() {
		connected.set(false);
		kickReason = null;
		connection.execute("session.rejoin", ignored -> { });
	}

	@Override
	public void connected(@NotNull Duration timeout) {
		connection.await(connected::get, "connect", timeout);
	}

	@Override
	public void disconnected(@NotNull Duration timeout) {
		connection.await(() -> !connected.get(), "disconnect", timeout);
	}

	@Override
	public @NotNull String kicked(@NotNull Duration timeout) {
		connection.await(() -> kickReason != null, "be kicked", timeout);
		return kickReason;
	}
}
