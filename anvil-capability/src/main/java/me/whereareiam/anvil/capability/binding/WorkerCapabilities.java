package me.whereareiam.anvil.capability.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Discovers native capability bindings, coordinates typed registration, and owns binding rollback.
 * @param <B> external SDK context selected by the outer native transport adapter
 */
public final class WorkerCapabilities<B> {
	private final Map<String, WorkerExtension<B>> extensions = new LinkedHashMap<>();

	public static @NotNull <B> WorkerCapabilities<B> discover(@NotNull String backendId, @NotNull Class<B> backendType, int protocolNumber) {
		List<WorkerExtension<?>> discovered = new ArrayList<>();
		ServiceLoader.load(WorkerExtension.class).forEach(discovered::add);

		return new WorkerCapabilities<>(backendId, backendType, protocolNumber, discovered);
	}

	public WorkerCapabilities(
			@NotNull String backendId,
			@NotNull Class<B> backendType,
			int protocolNumber,
			@NotNull Collection<? extends WorkerExtension<?>> candidates
	) {
		for (WorkerExtension<?> candidate : candidates) {
			if (!candidate.backendId().equals(backendId) || !candidate.supports(protocolNumber)) continue;
			if (candidate.backendType() != backendType)
				throw new IllegalArgumentException("Capability '" + candidate.id() + "' requires a different native context");
			@SuppressWarnings("unchecked")
			WorkerExtension<B> extension = (WorkerExtension<B>) candidate;
			if (extensions.putIfAbsent(extension.id(), extension) != null)
				throw new IllegalStateException("Duplicate native capability ID: " + extension.id());
		}
	}

	public @NotNull Set<String> capabilities() { return Set.copyOf(extensions.keySet()); }

	/**
	 * Installs one player's bindings into the caller's typed registration scope.
	 * Failed installation closes completed bindings; the caller must discard the corresponding
	 * dispatch scope so that partially registered operations cannot be invoked.
	 *
	 * @param player native SDK and lifecycle access for this player
	 * @param operations borrowed registry supplied by the native integration
	 * @return bindings owned until permanent player destruction
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<B> player, @NotNull OperationRegistry operations) {
		PlayerBindings bindings = new PlayerBindings(operations);
		try {
			for (WorkerExtension<B> extension : extensions.values())
				bindings.bindings.add(extension.bind(player, bindings));
			bindings.installing = false;

			return bindings;
		} catch (RuntimeException | Error failure) {
			try {
				bindings.close();
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	@RequiredArgsConstructor
	private static final class PlayerBindings implements OperationRegistry, WorkerBinding {
		private final OperationRegistry operations;
		private final Set<String> installed = new LinkedHashSet<>();
		private final List<WorkerBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		@Override
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			if (!installing) throw new IllegalStateException("Operations may only be registered while binding a player");

			String id = channelOperation.getId();
			if (id.isBlank() || (!id.contains(".") && !id.contains(":"))) throw new IllegalArgumentException("ChannelOperation must be namespaced: " + id);
			if (!installed.add(id)) throw new IllegalStateException("Duplicate native channelOperation: " + id);

			operations.register(channelOperation, handler);
		}

		@Override
		public void close() {
			Throwable failure = null;
			for (WorkerBinding binding : bindings.reversed()) {
				try { binding.close(); } catch (RuntimeException | Error cleanup) {
					if (failure == null) failure = cleanup;
					else if (failure != cleanup) failure.addSuppressed(cleanup);
				}
			}

			bindings.clear();
			installed.clear();
			installing = false;

			if (failure instanceof RuntimeException exception) throw exception;
			if (failure instanceof Error error) throw error;
		}
	}
}
