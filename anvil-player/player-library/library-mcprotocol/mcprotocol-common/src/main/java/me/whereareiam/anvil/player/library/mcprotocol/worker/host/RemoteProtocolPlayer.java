package me.whereareiam.anvil.player.library.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.player.api.ProtocolPlayer;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.model.GameConnection;
import me.whereareiam.anvil.player.api.model.PlayerRequest;
import me.whereareiam.anvil.player.api.model.ProtocolRelease;
import me.whereareiam.anvil.player.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.player.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.player.library.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.player.library.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.player.library.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.player.library.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Library-owned player handle and its typed player channel, backed by an isolated worker.
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
	private final CopyOnWriteArrayList<PlayerProfile> grantedProfiles = new CopyOnWriteArrayList<>();

	RemoteProtocolPlayer(
			@NotNull ProtocolWorkerProcess worker,
			@NotNull PlayerRequest request,
			@Nullable AuthenticationSession authentication
	) {
		this.worker = worker;
		this.request = request;
		this.events = new PlayerEventDispatcher(request.name(), worker::recordDiagnostic);
		this.requested = authentication == null
				? new PlayerProfile(request.username(),
						UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + request.username()).getBytes(StandardCharsets.UTF_8)))
				: new PlayerProfile(authentication.username(), authentication.uuid());

		worker.register(id, this);
		try {
			capabilities = create(authentication);
		} catch (RuntimeException | Error exception) {
			worker.unregister(id);
			try (events) { throw exception; }
		}
	}

	private WorkerPlayerCapabilities create(@Nullable AuthenticationSession authentication) {
		GameConnection connection = request.connection();
		WorkerPlayerOptions options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder()
						.name(requested.username())
						.uniqueId(requested.uniqueId())
						.build())
				.credentials(authentication == null ? null : WorkerCredentials.builder()
						.accessToken(authentication.accessToken())
						.sessionServer(authentication.sessionServer())
						.build())
				.minecraftVersion(request.clientVersion().toString())
				.language(request.language())
				.connection(WorkerConnection.builder()
						.host(connection.address().getHostString())
						.port(connection.address().getPort())
						.virtualHost(connection.virtualHost())
						.sourceAddress(connection.sourceAddress() == null ? null : connection.sourceAddress().getHostAddress())
						.build())
				.build();

		JsonNode installed = worker.control(WorkerControlOperation.CREATE_PLAYER, id, options);
		return codec.decodePayload(installed, WorkerPlayerCapabilities.class);
	}

	/**
	 * Records the profile a login grants before subscribers see the event, so that the granted profile read after the
	 * session reports its connection is already the new one. The profile arrives with the login success, before the
	 * client enters the game, and again with the connection.
	 */
	void event(@NotNull String type, @NotNull JsonNode payload) {
		if (type.equals(PlayerConnectionEvent.GRANTED.id())) {
			PlayerProfile login = codec.open(payload, PlayerProfile.class);
			granted = login;
			if (login != null) grantedProfiles.addIfAbsent(login);
		}
		if (type.equals(PlayerConnectionEvent.CHANGED.id())) {
			PlayerProfile login = codec.open(payload, PlayerConnectionEvent.class).granted();
			granted = login;
			if (login != null) grantedProfiles.addIfAbsent(login);
		}
		events.dispatch(type, payload);
	}

	@Override
	public @NotNull String name() {
		return request.name();
	}

	@Override
	public @NotNull MinecraftVersion clientVersion() {
		return request.clientVersion();
	}

	@Override
	public @NotNull String libraryId() {
		return McProtocolClient.LIBRARY_ID;
	}

	@Override
	public @NotNull ProtocolRelease release() {
		return worker.release();
	}

	@Override
	public @NotNull PlayerProfile requestedProfile() {
		return requested;
	}

	@Override
	public @Nullable PlayerProfile grantedProfile() {
		return granted;
	}

	@Override
	public @NotNull List<PlayerProfile> grantedProfiles() {
		return List.copyOf(grantedProfiles);
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
	public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
		if (request == null && operation.requestType() != Void.class)
			throw new IllegalArgumentException("Operation '" + operation.id() + "' requires a "
					+ operation.requestType().getName() + " request");

		ensureOpen();
		JsonNode result = worker.request(operation.id(), id, arguments -> arguments.setAll(codec.envelope(request)));
		return codec.open(result, operation.responseType());
	}

	@Override
	public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> event, @NotNull Consumer<E> listener) {
		ensureOpen();
		return events.subscribe(event.id(), payload -> listener.accept(codec.open(payload, event.payloadType())));
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
		return Set.copyOf(capabilities.capabilities());
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
		reasons.putAll(capabilities.unavailable());
		return Map.copyOf(reasons);
	}

	private void ensureOpen() {
		if (destroyed.get()) throw new IllegalStateException("Simulated player '" + name() + "' has been destroyed");
		events.throwIfFailed();
	}

}
