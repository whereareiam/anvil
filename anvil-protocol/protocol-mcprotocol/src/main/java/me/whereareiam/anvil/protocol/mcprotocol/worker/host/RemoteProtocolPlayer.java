package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Backend-owned player handle and capability-provider request channel backed by an isolated worker.
 */
final class RemoteProtocolPlayer implements ProtocolPlayer, ProtocolChannel {
	private static final Duration OBSERVATION_INTERVAL = Duration.ofMillis(25);
	private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";

	private final ProtocolWorkerProcess worker;
	private final PlayerRequest request;
	private final PlayerIdentity identity;
	private final PlayerEventDispatcher events;

	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final String id = UUID.randomUUID().toString();
	private final AtomicBoolean destroying = new AtomicBoolean();
	private final AtomicBoolean destroyed = new AtomicBoolean();

	RemoteProtocolPlayer(
			@NotNull ProtocolWorkerProcess worker,
			@NotNull PlayerRequest request,
			@Nullable AuthenticationSession authentication
	) {
		this.worker = worker;
		this.request = request;
		this.events = new PlayerEventDispatcher(request.getName(), worker::recordDiagnostic);
		this.identity = PlayerIdentity.builder()
				.username(authentication == null
						? request.getName()
						: authentication.getUsername())
				.clientUniqueId(authentication == null
						? UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + request.getName()).getBytes(StandardCharsets.UTF_8))
						: authentication.getUuid())
				.build();

		worker.register(id, this);
		try {
			create(authentication);
		} catch (RuntimeException | Error exception) {
			worker.unregister(id);
			try (events) { throw exception; }
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
		events.dispatch(type, payload);
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
		return Optional.empty();
	}

	@Override
	public boolean destroyed() {
		return destroyed.get();
	}

	@Override
	public void destroy() {
		if (!destroying.compareAndSet(false, true)) return;

		try (events) {
			events.stop();
			destroyed.set(true);
			worker.control(WorkerControlOperation.DESTROY_PLAYER, id, Map.of());
		} finally {
			worker.unregister(id);
		}
	}

	@Override
	public @NotNull Optional<ProtocolChannel> channel() { return Optional.of(this); }

	@Override
	public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) {
		ensureOpen();
		JsonNode result = worker.request(operation, id, arguments -> arguments.setAll(WorkerMessageCodec.message(request)));
		return codec.messageBytes(result);
	}

	@Override
	public @NotNull ProtocolSubscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
		ensureOpen();
		return events.subscribe(event, encoded -> listener.accept(codec.messageBytes(encoded)));
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
			events.throwIfFailed();
			if (condition.getAsBoolean()) return;

			try {
				Thread.sleep(OBSERVATION_INTERVAL);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for player '" + name() + "'", exception);
			}
		}

		throw new IllegalStateException("Player '" + name() + "' did not " + description + " within " + timeout
				+ "; events=" + events.history() + worker.diagnosticTail());
	}

	@Override
	public @NotNull Set<String> installedCapabilities() {
		return worker.workerCapabilities();
	}

	private void ensureOpen() {
		if (destroyed.get()) throw new IllegalStateException("Simulated player '" + name() + "' has been destroyed");
		events.throwIfFailed();
	}

}
