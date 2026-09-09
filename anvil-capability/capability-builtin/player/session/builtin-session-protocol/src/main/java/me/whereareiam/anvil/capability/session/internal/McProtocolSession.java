package me.whereareiam.anvil.capability.session.internal;

import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.session.SessionConnection;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session capability driven by namespaced MCProtocol worker operations and events.
 */
public final class McProtocolSession implements Session {
	private final SessionConnection connection;
	private final AtomicBoolean connected = new AtomicBoolean();
	private volatile String kickReason;

	public McProtocolSession(SessionConnection connection) {
		this.connection = connection;
		connection.observe(event -> {
			connected.set(event.connected());
			if (!event.connected()) kickReason = event.kickReason();
		});
		connection.destroyed(() -> connected.set(false));
	}

	@Override
	public @NotNull SessionState state() {
		return SessionState.builder().connected(connected.get()).kickReason(kickReason).build();
	}

	@Override
	public void connect() {
		connected.set(false);
		kickReason = null;
		connection.connect();
	}

	@Override
	public void disconnect() {
		connection.disconnect();
	}

	@Override
	public void rejoin() {
		connected.set(false);
		kickReason = null;
		connection.rejoin();
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
