package me.whereareiam.anvil.player.library.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.edition.java.protocol.worker.JavaPacketDispatcher;
import me.whereareiam.anvil.player.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.player.worker.NativeSessionBindings;
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
 *
 * <p>It also decides whether the current session counts as connected: from the moment its channel reports the
 * connection open until the connection closed. The close reaches the player's protocol first, under its client's lock,
 * when the channel reports it, the player itself disconnects or the session reports its end, whichever comes first, and
 * only then does the session count as gone, for the player's sends and the bindings of its connection; a tick of the
 * client just before finds the connection and one just after finds none, as the vanilla client goes on ticking across a
 * closed connection.</p>
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
	private volatile @NotNull Connection connection = Connection.CONNECTING;
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
	 * Expects the reports of a new session, which connects until its channel reports the connection open and whose end
	 * is reported once. The player calls it while no session is current, so no report of the replaced one changes it.
	 */
	void connecting() {
		disconnectNotified.set(false);
		connection = Connection.CONNECTING;
		disconnecting = false;
	}

	/**
	 * Takes the end of the current session as the player's own disconnect, which closes its connection for the player's
	 * protocol at once.
	 *
	 * @param current the session the player disconnects
	 */
	void disconnecting(@NotNull Object current) {
		disconnecting = true;
		close(current);
	}

	/**
	 * Closes the connection of a session the player replaces with a new one for the player's protocol, before the
	 * replacement opens.
	 *
	 * @param current the replaced session
	 */
	void replacing(@NotNull Object current) {
		close(current);
	}

	/**
	 * Tells whether the current session counts as connected: its channel reported the connection open, and the
	 * player's protocol has not heard of its close.
	 *
	 * @return whether the player may send on the current session
	 */
	boolean connected() {
		return connection == Connection.OPEN;
	}

	@Override
	public void opened(@NotNull Object current) {
		protocol.player().run(() -> {
			if (player.isCurrentNativeSession(current) && connection == Connection.CONNECTING)
				connection = Connection.OPEN;
		});
	}

	@Override
	public void closed(@NotNull Object current) {
		close(current);
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
			close(current);
			sessions.end(current);
		} finally {
			player.emit(PlayerConnectionEvent.CHANGED, new PlayerConnectionEvent(false, reason, cause, null));
		}
	}

	/**
	 * Tells the player's protocol once that the current session's connection closed, and only then counts the session
	 * as gone, both under the client's lock, so no tick runs between them. The lock also orders the close against the
	 * player's replacement of the session, which closes it under the lock before a new session becomes current.
	 */
	private void close(Object current) {
		protocol.player().run(() -> {
			if (!player.isCurrentNativeSession(current) || connection == Connection.CLOSED) return;

			protocol.disconnected();
			connection = Connection.CLOSED;
		});
	}

	/**
	 * Where the current session's connection stands for the player.
	 */
	private enum Connection {
		/**
		 * The session connects; its channel has not reported the connection open.
		 */
		CONNECTING,
		/**
		 * The channel reported the connection open, and its close has not reached the player's protocol.
		 */
		OPEN,
		/**
		 * The player's protocol heard of the connection's close.
		 */
		CLOSED
	}
}
