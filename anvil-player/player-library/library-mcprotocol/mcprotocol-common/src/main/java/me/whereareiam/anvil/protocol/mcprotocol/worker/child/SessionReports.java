package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.worker.JavaPacketDispatcher;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.worker.NativeSessionBindings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns what the release's client reports of one worker player's sessions into the player's events and deliveries:
 * its granted profile and login, its packets, which the player's Java Edition protocol delivers, and the end of its
 * connection, reported once per session with the reason that decides it. Reports of a replaced session are ignored.
 */
final class SessionReports implements ClientListener<Object> {
	/**
	 * How long a closed connection waits for the reason of its closing. Client libraries handle packets on
	 * another thread than connection events, so the close can be reported before the server's disconnect packet.
	 */
	private static final long CLOSE_REASON_GRACE_MILLIS = 300;
	private static final ScheduledExecutorService CLOSE_REPORTS = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "anvil-close-reports");
		thread.setDaemon(true);
		return thread;
	});

	private final @NotNull PlayerBindingContext<Object> player;
	private final @NotNull JavaPacketDispatcher protocol;
	private final @NotNull NativeSessionBindings sessions;
	private final AtomicBoolean disconnectNotified = new AtomicBoolean();
	private volatile boolean disconnecting;

	/**
	 * @param player the player, which names its current session and emits its events
	 * @param protocol the player's Java Edition protocol, which delivers its packets
	 * @param sessions the player's native session bindings, whose session ends with its connection
	 */
	SessionReports(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull JavaPacketDispatcher protocol,
			@NotNull NativeSessionBindings sessions
	) {
		this.player = player;
		this.protocol = protocol;
		this.sessions = sessions;
	}

	/**
	 * Expects the reports of a new session, whose end is reported once.
	 */
	void opened() {
		disconnectNotified.set(false);
		disconnecting = false;
	}

	/**
	 * Takes the end of the current session as the player's own disconnect.
	 */
	void disconnecting() {
		disconnecting = true;
	}

	@Override
	public void granted(@NotNull Object current, @NotNull ClientProfile granted) {
		if (!player.isCurrentNativeSession(current)) return;
		player.emit(PlayerConnectionEvent.GRANTED, new PlayerProfile(granted.getName(), granted.getUniqueId()));
	}

	@Override
	public void loggedIn(@NotNull Object current, @NotNull ClientProfile granted) {
		if (!player.isCurrentNativeSession(current)) return;
		PlayerProfile profile = new PlayerProfile(granted.getName(), granted.getUniqueId());
		player.emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(true, null, null, profile));
	}

	@Override
	public void disconnected(@NotNull Object current, @NotNull DisconnectCause cause, @NotNull String reason) {
		if (!player.isCurrentNativeSession(current)) return;
		if (disconnecting) {
			report(current, DisconnectCause.CLIENT, reason);
			return;
		}
		if (cause != DisconnectCause.CONNECTION_LOST) {
			report(current, cause, reason);
			return;
		}

		// The reason of this close may still be on its way; it wins when it arrives within the grace period.
		CLOSE_REPORTS.schedule(() -> report(current, cause, reason), CLOSE_REASON_GRACE_MILLIS, TimeUnit.MILLISECONDS);
	}

	@Override
	public void received(@NotNull Object current, @NotNull ClientboundPacket packet) {
		if (!player.isCurrentNativeSession(current)) return;
		protocol.deliver(packet);
	}

	@Override
	public boolean wantsFrames() {
		return protocol.wantsFrames();
	}

	@Override
	public void received(
			@NotNull Object current,
			@NotNull ConnectionState state,
			int id,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
		if (!player.isCurrentNativeSession(current)) return;
		protocol.deliver(state, id, payload, packet);
	}

	@Override
	public boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		return protocol.wants(type);
	}

	private void report(Object current, DisconnectCause cause, String reason) {
		if (!player.isCurrentNativeSession(current)) return;
		if (!disconnectNotified.compareAndSet(false, true)) return;

		try {
			protocol.disconnected();
			sessions.end(current);
		} finally {
			player.emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, reason, cause, null));
		}
	}
}
