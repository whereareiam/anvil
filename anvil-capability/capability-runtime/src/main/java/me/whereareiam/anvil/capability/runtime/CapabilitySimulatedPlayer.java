package me.whereareiam.anvil.capability.runtime;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Default player facade composed from dependency-discovered capabilities.
 */
final class CapabilitySimulatedPlayer implements SimulatedPlayer {
	private final ProtocolPlayer player;
	private final Consumer<SimulatedPlayer> onDestroyed;
	private final AtomicBoolean destroyed = new AtomicBoolean();
	private final List<Runnable> cleanup = new ArrayList<>();
	private final Map<Class<?>, Object> capabilities = new LinkedHashMap<>();
	private final Collection<?> services;

	CapabilitySimulatedPlayer(
			ProtocolPlayer player,
			List<PlayerCapabilityProvider<?>> providers,
			Collection<?> services,
			Consumer<SimulatedPlayer> onDestroyed
	) {
		this.player = player;
		this.services = List.copyOf(services);
		this.onDestroyed = onDestroyed;
		try {
			for (PlayerCapabilityProvider<?> provider : providers)
				create(provider, new Context(Set.copyOf(provider.descriptor().getRequiredCapabilities())));
		} catch (RuntimeException exception) {
			runCleanup(this::releaseCapabilities, exception);
			throw exception;
		}
	}

	private <C extends PlayerCapability> void create(
			PlayerCapabilityProvider<C> provider,
			PlayerCapabilityContext context
	) {
		try {
			C capability = provider.create(context);
			if (!provider.capability().isInstance(capability))
				throw new CapabilityException("Provider '" + provider.descriptor().getId()
						+ "' returned " + capability.getClass().getName() + " instead of "
						+ provider.capability().getName());
			capabilities.put(provider.capability(), capability);
		} catch (CapabilityException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new CapabilityException("Could not create provider '" + provider.descriptor().getId()
					+ "' for player '" + name() + "'", exception);
		}
	}

	@Override
	public @NotNull String name() {
		return player.name();
	}

	@Override
	public @NotNull String clientVersion() {
		return player.clientVersion();
	}

	@Override
	public @NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type) {
		Object capability = capabilities.get(type);
		if (capability == null)
			throw new CapabilityUnavailableException("Player '" + name() + "' has no capability " + type.getName()
					+ ". Available: " + capabilities.keySet().stream().map(Class::getName).toList());
		return type.cast(capability);
	}

	@Override
	public boolean hasCapability(@NotNull Class<? extends PlayerCapability> type) {
		return capabilities.containsKey(type);
	}

	@Override
	public @NotNull PlayerState state() {
		return PlayerState.builder().destroyed(destroyed.get() || player.destroyed()).build();
	}

	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true))
			return;
		RuntimeException failure = runCleanup(player::destroy, null);
		failure = runCleanup(this::releaseCapabilities, failure);
		failure = runCleanup(() -> onDestroyed.accept(this), failure);
		if (failure != null)
			throw failure;
	}

	private void releaseCapabilities() {
		RuntimeException failure = null;
		for (Runnable action : new ArrayList<>(cleanup).reversed())
			failure = runCleanup(action, failure);
		cleanup.clear();
		if (failure != null)
			throw failure;
	}

	private @Nullable RuntimeException runCleanup(@NotNull Runnable action, @Nullable RuntimeException failure) {
		try {
			action.run();
		} catch (RuntimeException exception) {
			if (failure == null)
				return exception;
			if (failure != exception)
				failure.addSuppressed(exception);
		}
		return failure;
	}

	@RequiredArgsConstructor
	private final class Context implements PlayerCapabilityContext {
		private final Set<Class<? extends PlayerCapability>> dependencies;

		@Override
		public @NotNull String playerName() {
			return name();
		}

		@Override
		public @NotNull String clientVersion() {
			return CapabilitySimulatedPlayer.this.clientVersion();
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			for (Object service : services)
				if (type.isInstance(service))
					return Optional.of(type.cast(service));
			return player.findService(type);
		}

		@Override
		public @NotNull <C extends PlayerCapability> Optional<C> findCapability(@NotNull Class<C> type) {
			if (!dependencies.contains(type))
				return Optional.empty();
			return Optional.ofNullable(capabilities.get(type)).map(type::cast);
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			cleanup.add(action);
		}
	}
}
