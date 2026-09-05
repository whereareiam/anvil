package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolPacketListener;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolWorkerOperation;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Validated operation and packet-listener registry for worker-side capabilities.
 */
final class WorkerCapabilityRegistry implements ProtocolCapabilityAdapterRegistry {
	private final Map<String, ProtocolWorkerOperation> operations = new LinkedHashMap<>();
	private final List<ProtocolPacketListener> packetListeners = new ArrayList<>();
	private final Set<String> capabilities = new LinkedHashSet<>();
	private @Nullable String installing;

	static @NotNull WorkerCapabilityRegistry discover(int protocolNumber) {
		var providers = ServiceLoader.load(ProtocolCapabilityAdapterProvider.class).stream()
				.map(ServiceLoader.Provider::get).toList();
		var direct = ServiceLoader.load(ProtocolCapabilityAdapter.class).stream().map(ServiceLoader.Provider::get).toList();
		return compose(protocolNumber, providers, direct);
	}

	static WorkerCapabilityRegistry compose(
			int protocolNumber,
			Collection<ProtocolCapabilityAdapterProvider> providers,
			Collection<ProtocolCapabilityAdapter> direct
	) {
		var selected = providers.stream()
				.filter(provider -> provider.supports(protocolNumber))
				.map(ProtocolCapabilityAdapterProvider::create);
		var adapters = Stream.concat(selected, direct.stream()).toList();
		return new WorkerCapabilityRegistry(protocolNumber, adapters);
	}

	WorkerCapabilityRegistry(int protocolNumber, @NotNull Collection<ProtocolCapabilityAdapter> adapters) {
		adapters.stream().filter(adapter -> adapter.supports(protocolNumber)).forEach(this::install);
	}

	private void install(ProtocolCapabilityAdapter capability) {
		if (!capabilities.add(capability.id())) throw new IllegalStateException("Duplicate MCProtocol worker capability ID: " + capability.id());

		installing = capability.id();
		try {
			capability.install(this);
		} finally {
			installing = null;
		}
	}

	@Override
	public void operation(@NotNull String operation, @NotNull ProtocolWorkerOperation handler) {
		if (installing == null) throw new IllegalStateException("Worker operations may only be registered while installing a capability");
		if (WorkerControlOperation.find(operation).isPresent()) throw new IllegalArgumentException("Worker lifecycle operation is reserved: " + operation);
		if (operation.isBlank() || (!operation.contains(".") && !operation.contains(":"))) throw new IllegalArgumentException("Worker operation must be namespaced: " + operation);

		ProtocolWorkerOperation duplicate = operations.putIfAbsent(operation, handler);
		if (duplicate != null) throw new IllegalStateException("Duplicate MCProtocol worker operation '" + operation + "'");
	}

	@Override
	public void packets(@NotNull ProtocolPacketListener listener) {
		if (installing == null) throw new IllegalStateException("Packet listeners may only be registered while installing a capability");
		packetListeners.add(listener);
	}

	@NotNull JsonNode execute(@NotNull String operation, @NotNull ProtocolWorkerPlayer player, @NotNull JsonNode arguments) throws Exception {
		ProtocolWorkerOperation handler = operations.get(operation);
		if (handler == null)
			throw new IllegalArgumentException("Unknown protocol operation: " + operation
					+ ". Installed capabilities: " + capabilities);
		return handler.execute(player, arguments);
	}

	void packet(@NotNull ProtocolWorkerPlayer player, @NotNull Object packet) {
		for (ProtocolPacketListener listener : packetListeners)
			listener.received(player, packet);
	}

	@NotNull Set<String> capabilities() {
		return Set.copyOf(capabilities);
	}
}
