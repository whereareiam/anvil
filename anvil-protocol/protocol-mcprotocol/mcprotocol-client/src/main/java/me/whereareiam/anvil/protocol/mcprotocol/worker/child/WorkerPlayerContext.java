package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerContext;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerSession;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerPlayerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared child-side state. The selected binding owns only its native connection and packets.
 */
@Accessors(fluent = true)
final class WorkerPlayerContext implements ProtocolWorkerPlayer, ProtocolWorkerContext, AutoCloseable {
	private final String id;
	@Getter private final @NotNull String name;
	@Getter private final @NotNull UUID uuid;
	@Getter private final @NotNull String host;
	@Getter private final int port;
	@Getter private final @Nullable String accessToken;
	@Getter private final @NotNull ObjectMapper mapper = new ObjectMapper();
	@Getter private volatile float yaw;
	@Getter private volatile float pitch;
	private final WorkerMessageWriter events;
	private final WorkerCapabilityRegistry capabilities;
	private final ProtocolWorkerSession session;
	private final AtomicInteger sequence = new AtomicInteger();
	private final Map<Class<?>, Object> capabilityState = new ConcurrentHashMap<>();

	WorkerPlayerContext(
			String id,
			WorkerPlayerOptions options,
			WorkerMessageWriter events,
			WorkerCapabilityRegistry capabilities,
			ProtocolWorkerBinding binding
	) {
		this.id = id;
		name = options.getName();
		uuid = options.getUuid();
		host = options.getHost();
		port = options.getPort();
		accessToken = options.getAccessToken();
		this.events = events;
		this.capabilities = capabilities;
		session = binding.create(this);
	}

	@Override
	public @NotNull ProtocolWorkerPlayer player() {
		return this;
	}

	@Override
	public void received(@NotNull Object packet) {
		capabilities.packet(this, packet);
	}

	@Override
	public void connected() {
		emit(WorkerPlayerEvent.CONNECTED.getWireName(), ignored -> { });
	}

	@Override
	public void disconnected(@NotNull String reason) {
		emit(WorkerPlayerEvent.DISCONNECTED.getWireName(), value -> value.put("reason", reason));
	}

	@Override
	public void connect() {
		session.connect();
	}

	@Override
	public void disconnect() {
		session.disconnect();
	}

	@Override
	public void rejoin() {
		session.rejoin();
	}

	@Override
	public void send(@NotNull Object packet) {
		session.send(packet);
	}

	@Override
	public void emit(@NotNull String event, @NotNull Consumer<ObjectNode> payload) {
		ObjectNode value = mapper.createObjectNode();
		payload.accept(value);
		events.event(id, event, value);
	}

	@Override
	public @NotNull <T> T state(@NotNull Class<T> key, @NotNull Supplier<T> factory) {
		return key.cast(capabilityState.computeIfAbsent(key, ignored -> factory.get()));
	}

	@Override
	public void view(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	@Override
	public int nextSequence() {
		return sequence.incrementAndGet();
	}

	@Override
	public void close() {
		try {
			session.close();
		} finally {
			capabilityState.clear();
		}
	}
}
