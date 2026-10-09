package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Discovers, validates, orders, and composes dependency-provided player capabilities for the players
 * of one protocol library.
 *
 * <p>For a player with a native worker, a protocol-backed provider whose capability the worker does not
 * install is skipped together with every provider depending on it. The skipped capability reports the
 * worker's reason, such as the member missing from the loaded release, or that the library's worker does not
 * install it at all; one warning line per player lists what was skipped. A player without a native worker,
 * such as one whose library works only through its own services, is composed with every provider.</p>
 */
public final class PlayerCapabilityRuntime {
	private final CapabilityRuntime<PlayerCapability, PlayerCapabilityContext> runtime;
	private final Set<String> protocolProviders;

	/**
	 * Discovers every provider from the current thread context class loader.
	 *
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover() {
		return discover((String) null);
	}

	/**
	 * Discovers shared player providers and protocol providers compatible with one protocol library.
	 *
	 * @param libraryId protocol-library identifier, or {@code null} for all providers
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@Nullable String libraryId) {
		return discover(contextLoader(), libraryId, List.of());
	}

	/**
	 * Discovers shared and protocol providers for one library from the current thread context class
	 * loader and combines explicitly adapted scenario providers before dependency validation.
	 *
	 * @param libraryId protocol-library identifier of the composed players
	 * @param additional assembly-supplied provider adapters
	 * @return validated capability composer
	 */
	public static @NotNull PlayerCapabilityRuntime discover(
			@NotNull String libraryId,
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> additional
	) {
		return discover(contextLoader(), libraryId, additional);
	}

	/**
	 * Discovers every provider from an explicit class loader.
	 *
	 * @param loader provider class loader
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@NotNull ClassLoader loader) {
		return discover(loader, null, List.of());
	}

	/**
	 * Discovers shared and protocol providers for one library from an explicit class loader and
	 * combines explicitly adapted scenario providers before dependency validation. Additional providers
	 * retain their own scoped service ownership.
	 *
	 * @param loader provider class loader
	 * @param libraryId protocol-library identifier of the composed players, or {@code null} for all providers
	 * @param additional assembly-supplied provider adapters
	 * @return validated capability composer
	 */
	public static @NotNull PlayerCapabilityRuntime discover(
			@NotNull ClassLoader loader,
			@Nullable String libraryId,
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> additional
	) {
		List<PlayerCapabilityProvider<?>> combined = loadProviders(loader);
		combined.addAll(additional);

		return new PlayerCapabilityRuntime(combined, loadProtocolProviders(loader, libraryId));
	}

	private static ClassLoader contextLoader() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return loader == null ? PlayerCapabilityRuntime.class.getClassLoader() : loader;
	}

	private static @NotNull List<PlayerCapabilityProvider<?>> loadProviders(@NotNull ClassLoader loader) {
		List<PlayerCapabilityProvider<?>> providers = new ArrayList<>();
		for (PlayerCapabilityProvider<?> provider : ServiceLoader.load(PlayerCapabilityProvider.class, loader))
			providers.add(provider);

		return providers;
	}

	private static @NotNull List<ProtocolPlayerCapabilityProvider<?>> loadProtocolProviders(
			@NotNull ClassLoader loader,
			@Nullable String libraryId
	) {
		List<ProtocolPlayerCapabilityProvider<?>> providers = new ArrayList<>();
		for (ProtocolPlayerCapabilityProvider<?> provider : ServiceLoader.load(ProtocolPlayerCapabilityProvider.class, loader))
			if (libraryId == null || provider.supportedLibraries().isEmpty()
					|| provider.supportedLibraries().contains(libraryId))
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
	 * @param protocolProviders protocol factories selected for one protocol library
	 */
	public PlayerCapabilityRuntime(
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers,
			@NotNull Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders
	) {
		List<PlayerCapabilityProvider<?>> combined = new ArrayList<>(providers);
		Set<String> protocol = new LinkedHashSet<>();
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders) {
			combined.add(new ProtocolPlayerCapabilityProviderAdapter<>(provider));
			protocol.add(provider.descriptor().getId());
		}

		this.runtime = new CapabilityRuntime<>(PlayerCapability.class, combined);
		this.protocolProviders = Set.copyOf(protocol);
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
	 * Composes all discovered capabilities around one library-owned player.
	 * Protocol factories require a protocol-specific player input; shared factories need only the
	 * player lifetime and observations. All matching factories share one dependency graph. When the
	 * player has a native channel, protocol factories whose capability its worker does not install are
	 * skipped with the worker's reason, or with the note that the library's worker does not install them,
	 * together with their dependents.
	 *
	 * @param player library-owned player lifetime and any specialized inputs
	 * @param observation player identity and route observation
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return extensible simulated player
	 */
	public @NotNull SimulatedPlayer compose(
			@NotNull CapabilityPlayer player,
			@NotNull PlayerObservation observation,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	) {
		return new CapabilitySimulatedPlayer(player, runtime, unavailable(player), observation, onDestroyed);
	}

	private Map<String, String> unavailable(CapabilityPlayer player) {
		if (!(player instanceof ProtocolCapabilityPlayer protocol)) return Map.of();

		Optional<CapabilityChannel> channel = protocol.channel();
		if (channel.isEmpty()) return Map.of();

		Set<String> installed = channel.get().installedCapabilities();
		Map<String, String> reported = channel.get().unavailableCapabilities();
		Map<String, String> skipped = new LinkedHashMap<>();
		for (String id : protocolProviders) {
			if (installed.contains(id)) continue;

			String reason = reported.get(id);
			skipped.put(id, reason == null ? "not installed by the " + protocol.libraryId() + " worker" : reason);
		}

		return skipped;
	}
}
