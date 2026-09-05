package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerPlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Backend-owned player handle and capability-provider request channel backed by an isolated worker.
 */
final class RemoteProtocolPlayer implements ProtocolPlayer, ProtocolPlayerConnection {
	private static final int EVENT_HISTORY_LIMIT = 80;
	private static final Duration OBSERVATION_INTERVAL = Duration.ofMillis(25);
	private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";

	private final ProtocolWorkerProcess worker;
	private final PlayerRequest request;
	private final String id = UUID.randomUUID().toString();
	private final AtomicBoolean destroyed = new AtomicBoolean();
	private final PlayerIdentity identity;
	private final Map<String, CopyOnWriteArrayList<Consumer<JsonNode>>> listeners = new ConcurrentHashMap<>();
	private final Deque<String> events = new ArrayDeque<>();

	RemoteProtocolPlayer(
			@NotNull ProtocolWorkerProcess worker,
			@NotNull PlayerRequest request,
			@Nullable AuthenticationSession authentication
	) {
		this.worker = worker;
		this.request = request;
		this.identity = PlayerIdentity.builder()
				.username(authentication == null ? request.getName() : authentication.getUsername())
				.clientUniqueId(authentication == null
						? UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + request.getName()).getBytes(StandardCharsets.UTF_8))
						: authentication.getUuid())
				.build();

		worker.register(id, this);
		try {
			create(authentication);
		} catch (RuntimeException exception) {
			worker.unregister(id);
			throw exception;
		}
	}

	private void create(@Nullable AuthenticationSession authentication) {
		WorkerPlayerOptions options = WorkerPlayerOptions.builder()
				.name(identity.getUsername())
				.uuid(identity.getClientUniqueId())
				.host(request.getAddress().getHostString())
				.port(request.getAddress().getPort())
				.accessToken(authentication == null ? null : authentication.getAccessToken())
				.build();

		worker.control(WorkerControlOperation.CREATE_PLAYER, id, options);
	}

	void event(@NotNull String type, @NotNull JsonNode payload) {
		remember(type + " " + payload);
		var subscribed = listeners.get(type);
		if (subscribed == null) return;

		subscribed.forEach(listener -> listener.accept(payload));
	}

	@Override
	public @NotNull String name() {
		return request.getName();
	}

	@Override
	public @NotNull String clientVersion() {
		return request.getClientVersion();
	}

	@Override
	public @NotNull PlayerIdentity identity() {
		return identity;
	}

	@Override
	public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
		return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
	}

	@Override
	public boolean destroyed() {
		return destroyed.get();
	}

	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true)) return;

		try {
			worker.control(WorkerControlOperation.DESTROY_PLAYER, id, Map.of());
		} finally {
			try {
				event(WorkerPlayerEvent.DESTROYED.getWireName(), JsonNodeFactory.instance.objectNode());
			} finally {
				worker.unregister(id);
				listeners.clear();
			}
		}
	}

	@Override
	public @NotNull JsonNode request(
			@NotNull String operation,
			@NotNull Consumer<ObjectNode> arguments
	) {
		ensureOpen();
		return worker.request(operation, id, arguments);
	}

	@Override
	public void subscribe(@NotNull String event, @NotNull Consumer<JsonNode> listener) {
		ensureOpen();
		listeners.computeIfAbsent(event, ignored -> new CopyOnWriteArrayList<>()).add(listener);
	}

	@Override
	public void await(
			@NotNull BooleanSupplier condition,
			@NotNull String description,
			@NotNull Duration timeout
	) {
		if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");

		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) return;

			try {
				Thread.sleep(OBSERVATION_INTERVAL);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for player '" + name() + "'", exception);
			}
		}

		throw new IllegalStateException("Player '" + name() + "' did not " + description + " within " + timeout
				+ "; events=" + eventHistory() + worker.diagnosticTail());
	}

	@Override
	public @NotNull Set<String> workerCapabilities() {
		return worker.workerCapabilities();
	}

	private void ensureOpen() {
		if (destroyed.get()) throw new IllegalStateException("Simulated player '" + name() + "' has been destroyed");
	}

	private void remember(String event) {
		synchronized (events) {
			events.addLast(event);
			while (events.size() > EVENT_HISTORY_LIMIT) events.removeFirst();
		}
	}

	private String eventHistory() {
		synchronized (events) {
			return events.toString();
		}
	}
}
