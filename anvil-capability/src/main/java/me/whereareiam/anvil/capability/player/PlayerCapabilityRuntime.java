package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Discovers, validates, orders, and composes dependency-provided player capabilities.
 */
public final class PlayerCapabilityRuntime {
	private final CapabilityRuntime<PlayerCapability, PlayerCapabilityContext> runtime;

	/**
	 * Discovers providers from the current thread context class loader.
	 *
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover() {
		return discover((String) null);
	}

	/**
	 * Discovers shared player providers and protocol providers compatible with one selected backend.
	 *
	 * @param protocolId selected protocol-provider ID, or {@code null} for all providers
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@Nullable String protocolId) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return discover(loader == null ? PlayerCapabilityRuntime.class.getClassLoader() : loader, protocolId);
	}

	/**
	 * Discovers shared and protocol providers and combines explicitly adapted scenario providers before
	 * dependency validation. Additional providers retain their own scoped service ownership.
	 *
	 * @param protocolId selected protocol backend
	 * @param additional assembly-supplied provider adapters
	 * @return validated capability composer
	 */
	public static @NotNull PlayerCapabilityRuntime discover(
			@NotNull String protocolId,
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> additional
	) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		ClassLoader selected = loader == null ? PlayerCapabilityRuntime.class.getClassLoader() : loader;
		List<PlayerCapabilityProvider<?>> combined = loadProviders(selected);
		combined.addAll(additional);
		return new PlayerCapabilityRuntime(combined, loadProtocolProviders(selected, protocolId));
	}

	/**
	 * Discovers providers from an explicit class loader.
	 *
	 * @param loader provider class loader
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@NotNull ClassLoader loader) {
		return discover(loader, null);
	}

	private static @NotNull PlayerCapabilityRuntime discover(
			@NotNull ClassLoader loader,
			@Nullable String protocolId
	) {
		return new PlayerCapabilityRuntime(loadProviders(loader), loadProtocolProviders(loader, protocolId));
	}

	private static @NotNull List<PlayerCapabilityProvider<?>> loadProviders(@NotNull ClassLoader loader) {
		List<PlayerCapabilityProvider<?>> providers = new ArrayList<>();
		for (PlayerCapabilityProvider<?> provider : ServiceLoader.load(PlayerCapabilityProvider.class, loader))
			providers.add(provider);

		return providers;
	}

	private static @NotNull List<ProtocolPlayerCapabilityProvider<?>> loadProtocolProviders(
			@NotNull ClassLoader loader,
			@Nullable String protocolId
	) {
		List<ProtocolPlayerCapabilityProvider<?>> providers = new ArrayList<>();
		for (ProtocolPlayerCapabilityProvider<?> provider : ServiceLoader.load(ProtocolPlayerCapabilityProvider.class, loader))
			if (protocolId == null || provider.supportedProtocolIds().isEmpty()
					|| provider.supportedProtocolIds().contains(protocolId))
				providers.add(provider);

		return providers;
	}

	/**
	 * Creates a runtime from explicit providers, primarily for embedding and contract tests.
	 *
	 * @param providers capability providers
	 */
	public PlayerCapabilityRuntime(@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers) {
		this(providers, List.of());
	}

	/**
	 * Combines shared and already selected protocol factories into one dependency graph.
	 * Capability creation remains deferred until the player is composed.
	 *
	 * @param providers shared player factories, including assembly-adapted agent factories
	 * @param protocolProviders protocol factories selected for the active backend
	 */
	public PlayerCapabilityRuntime(
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers,
			@NotNull Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders
	) {
		List<PlayerCapabilityProvider<?>> combined = new ArrayList<>(providers);
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders)
			combined.add(new ProtocolPlayerCapabilityProviderAdapter<>(provider));
		this.runtime = new CapabilityRuntime<>(PlayerCapability.class, combined);
	}

	/**
	 * Returns provider descriptors in dependency order.
	 *
	 * @return immutable descriptor list
	 */
	public @NotNull List<CapabilityDescriptor> providers() {
		return runtime.providers();
	}

	/**
	 * Composes all discovered capabilities around one backend-owned player.
	 * Protocol factories require a protocol-specific player input; shared factories need only the
	 * player lifetime and observations. All matching factories share one dependency graph.
	 *
	 * @param player backend-owned player lifetime and any specialized inputs
	 * @param observation player identity and route observation
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return extensible simulated player
	 */
	public @NotNull SimulatedPlayer compose(
			@NotNull CapabilityPlayer player,
			@NotNull PlayerObservation observation,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	) {
		return new CapabilitySimulatedPlayer(player, runtime, observation, onDestroyed);
	}
}
