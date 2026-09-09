package me.whereareiam.anvil.capability.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.CapabilitySet;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Default player facade composed from dependency-discovered capabilities.
 */
final class CapabilitySimulatedPlayer implements SimulatedPlayer {
	private final CapabilityPlayer player;
	private final PlayerObservation observation;
	private final Consumer<SimulatedPlayer> onDestroyed;
	private final CapabilitySet<PlayerCapability> capabilities;

	private final AtomicBoolean destroyed = new AtomicBoolean();

	CapabilitySimulatedPlayer(
			CapabilityPlayer player,
			CapabilityRuntime<PlayerCapability, PlayerCapabilityContext> runtime,
			PlayerObservation observation,
			Consumer<SimulatedPlayer> onDestroyed
	) {
		this.player = player;
		this.observation = observation;
		this.onDestroyed = onDestroyed;
		this.capabilities = runtime.compose("Player '" + player.name() + "'", dependencies ->
				player instanceof ProtocolCapabilityPlayer protocol
						? new ProtocolContext(dependencies, protocol)
						: new Context(dependencies));
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
		return capabilities.capability(type);
	}

	@Override
	public boolean hasCapability(@NotNull Class<? extends PlayerCapability> type) {
		return capabilities.hasCapability(type);
	}

	@Override
	public @NotNull PlayerState state() {
		return PlayerState.builder().destroyed(destroyed.get() || player.destroyed()).build();
	}

	@Override
	public void destroy() {
		if (!destroyed.compareAndSet(false, true)) return;

		Throwable failure = release(player::destroy, null);
		failure = release(capabilities::close, failure);
		failure = release(() -> onDestroyed.accept(this), failure);
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}

	private @Nullable Throwable release(@NotNull Runnable action, @Nullable Throwable failure) {
		try {
			action.run();
		} catch (RuntimeException | Error exception) {
			if (failure == null) return exception;
			if (failure != exception) failure.addSuppressed(exception);
		}

		return failure;
	}

	@RequiredArgsConstructor
	private class Context implements PlayerCapabilityContext {
		private final CapabilityContext<PlayerCapability> capabilities;

		@Override
		public @NotNull String playerName() {
			return name();
		}

		@Override
		public @NotNull String clientVersion() {
			return CapabilitySimulatedPlayer.this.clientVersion();
		}

		@Override
		public @NotNull PlayerObservation observation() {
			return observation;
		}

		@Override
		public @NotNull <C extends PlayerCapability> Optional<C> findCapability(@NotNull Class<C> type) {
			return capabilities.findCapability(type);
		}

		@Override
		public @NotNull <C extends PlayerCapability> C requireCapability(@NotNull Class<C> type) {
			return capabilities.requireCapability(type);
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			capabilities.onClose(action);
		}
	}

	private final class ProtocolContext extends Context implements ProtocolPlayerCapabilityContext {
		private final ProtocolCapabilityPlayer protocol;

		private ProtocolContext(CapabilityContext<PlayerCapability> capabilities, ProtocolCapabilityPlayer protocol) {
			super(capabilities);
			this.protocol = protocol;
		}

		@Override
		public @NotNull CapabilityChannel channel() {
			return protocol.channel().orElseThrow(() -> new CapabilityException(
					"Player '" + name() + "' has no capability channel"
			));
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return protocol.findService(type);
		}
	}
}
