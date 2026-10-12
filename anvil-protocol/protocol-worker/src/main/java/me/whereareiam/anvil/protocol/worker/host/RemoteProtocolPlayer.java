package me.whereareiam.anvil.protocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.worker.type.WorkerPlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Library-owned player handle and capability-provider request channel backed by an isolated worker.
 */
final class RemoteProtocolPlayer implements ProtocolPlayer, ProtocolChannel {
	private static final Duration OBSERVATION_INTERVAL = Duration.ofMillis(25);
	private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";

	private final ProtocolWorkerProcess worker;
	private final PlayerRequest request;
	private final PlayerProfile requested;
	private final PlayerEventDispatcher events;

	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final String id = UUID.randomUUID().toString();
	private final AtomicBoolean destroying = new AtomicBoolean();
	private final AtomicBoolean destroyed = new AtomicBoolean();
	private final WorkerPlayerCapabilities capabilities;
	private volatile @Nullable PlayerProfile granted;

	RemoteProtocolPlayer(
			@NotNull ProtocolWorkerProcess worker,
			@NotNull PlayerRequest request,
			@Nullable AuthenticationSession authentication
	) {
		this.worker = worker;
		this.request = request;
		this.events = new PlayerEventDispatcher(request.getName(), worker::recordDiagnostic);
		this.requested = authentication == null
				? new PlayerProfile(request.getUsername(),
						UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + request.getUsername()).getBytes(StandardCharsets.UTF_8)))
				: new PlayerProfile(authentication.getUsername(), authentication.getUuid());

		worker.register(id, this);
		try {
			capabilities = create(authentication);
		} catch (RuntimeException | Error exception) {
			worker.unregister(id);
			try (events) { throw exception; }
		}
	}

	private WorkerPlayerCapabilities create(@Nullable AuthenticationSession authentication) {
		GameConnection connection = request.getConnection();
		WorkerPlayerOptions options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder()
						.name(requested.getUsername())
						.uniqueId(requested.getUniqueId())
						.build())
				.credentials(authentication == null ? null : WorkerCredentials.builder()
						.accessToken(authentication.getAccessToken())
						.sessionServer(authentication.getSessionServer())
						.build())
				.connection(WorkerConnection.builder()
						.host(connection.getAddress().getHostString())
						.port(connection.getAddress().getPort())
						.virtualHost(connection.getVirtualHost())
						.sourceAddress(connection.getSourceAddress() == null ? null : connection.getSourceAddress().getHostAddress())
						.build())
				.build();

		JsonNode installed = worker.control(WorkerControlOperation.CREATE_PLAYER, id, options);
		return codec.decodePayload(installed, WorkerPlayerCapabilities.class);
	}

	/**
	 * Records the profile a connection event grants before subscribers see the event, so that an identity read
	 * after the session reports its connection already carries it.
	 */
	void event(@NotNull String type, @NotNull JsonNode payload) {
		if (type.equals(WorkerPlayerEvent.CONNECTION.getWireName()))
			granted = codec.granted(codec.messageBytes(payload)).orElse(null);
		events.dispatch(type, payload);
	}

	@Override
	public @NotNull String name() {
		return request.getName();
	}

	@Override
	public @NotNull String clientVersion() {
		return request.getClientVersion().toString();
	}

	@Override
	public @NotNull String libraryId() {
		return worker.library().getId();
	}

	@Override
	public @NotNull ProtocolRelease release() {
		return worker.release();
	}

	@Override
	public @NotNull PlayerIdentity identity() {
		return PlayerIdentity.builder().requested(requested).granted(granted).build();
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

	/**
	 * Returns the capabilities bound for this player when it was created.
	 *
	 * @return immutable capability IDs
	 */
	@Override
	public @NotNull Set<String> installedCapabilities() {
		return Set.copyOf(capabilities.getCapabilities());
	}

	/**
	 * Returns the capabilities this player's worker could not install, including those whose binding
	 * failed to link while creating this player.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	@Override
	public @NotNull Map<String, String> unavailableCapabilities() {
		Map<String, String> reasons = new LinkedHashMap<>(worker.unavailableCapabilities());
		reasons.putAll(capabilities.getUnavailable());
		return Map.copyOf(reasons);
	}

	private void ensureOpen() {
		if (destroyed.get()) throw new IllegalStateException("Simulated player '" + name() + "' has been destroyed");
		events.throwIfFailed();
	}

}
