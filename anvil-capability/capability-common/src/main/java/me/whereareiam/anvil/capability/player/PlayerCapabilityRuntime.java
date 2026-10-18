package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.CapabilityRuntime;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
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
import java.util.stream.Collectors;

/**
 * Discovers, validates, orders, and composes dependency-provided player capabilities for the players
 * of one protocol library.
 *
 * <p>A protocol-backed provider is skipped, together with every provider depending on it, when the player's library
 * does not speak a protocol it requires; when the player has no native worker and the provider is worker-backed; and,
 * for a player with a native worker, when the worker does not install its capability. The skipped capability reports
 * why, such as the protocol the library lacks or the member missing from the loaded release; one warning line per
 * player lists what was skipped.</p>
 */
public final class PlayerCapabilityRuntime {
	private final CapabilityRuntime<PlayerCapability, ProtocolPlayerCapabilityContext> runtime;
	private final List<ProtocolPlayerCapabilityProvider<?>> protocolProviders;
	private final Map<String, String> unsupported;

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
		return discover(contextLoader(), libraryId);
	}

	/**
	 * Discovers every provider from an explicit class loader.
	 *
	 * @param loader provider class loader
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@NotNull ClassLoader loader) {
		return discover(loader, null);
	}

	/**
	 * Discovers shared player providers and protocol providers compatible with one protocol library from an
	 * explicit class loader.
	 *
	 * @param loader provider class loader
	 * @param libraryId protocol-library identifier of the composed players, or {@code null} for all providers
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@NotNull ClassLoader loader, @Nullable String libraryId) {
		ProtocolLibraryProvider library = libraryId == null ? null : findLibrary(loader, libraryId);

		return new PlayerCapabilityRuntime(loadProviders(loader), loadProtocolProviders(loader, libraryId), library);
	}

	private static @Nullable ProtocolLibraryProvider findLibrary(
			@NotNull ClassLoader loader,
			@NotNull String libraryId
	) {
		return ServiceLoader.load(ProtocolLibraryProvider.class, loader).stream()
				.map(ServiceLoader.Provider::get)
				.filter(library -> library.id().equals(libraryId))
				.findFirst()
				.orElse(null);
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
	 * Combines shared and already selected protocol factories into one dependency graph, for players of any library.
	 * Capability creation remains deferred until the player is composed.
	 *
	 * @param providers shared player factories
	 * @param protocolProviders protocol factories selected for one protocol library
	 */
	public PlayerCapabilityRuntime(
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers,
			@NotNull Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders
	) {
		this(providers, protocolProviders, null);
	}

	/**
	 * Combines shared and already selected protocol factories into one dependency graph for the players of one
	 * library. A protocol factory that cannot apply to the library, because it requires a protocol the library's
	 * players do not speak or a worker the library does not run, yields to an applicable factory of the same
	 * capability, such as the library's own Session; without one it stays in the graph and is skipped with its reason.
	 * Capability creation remains deferred until the player is composed.
	 *
	 * @param providers shared player factories
	 * @param protocolProviders protocol factories selected for the library
	 * @param library the library whose players are composed, or {@code null} when it is not installed here
	 */
	public PlayerCapabilityRuntime(
			@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers,
			@NotNull Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders,
			@Nullable ProtocolLibraryProvider library
	) {
		Map<String, String> unsupported = library == null ? Map.of() : unsupported(protocolProviders, library);
		List<ProtocolPlayerCapabilityProvider<?>> selected = withoutYielding(providers, protocolProviders, unsupported);
		List<CapabilityProvider<? extends PlayerCapability, ? super ProtocolPlayerCapabilityContext>> combined =
				new ArrayList<>(providers);
		combined.addAll(selected);

		this.runtime = new CapabilityRuntime<>(PlayerCapability.class, combined);
		this.protocolProviders = List.copyOf(selected);
		this.unsupported = Map.copyOf(unsupported);
	}

	private static Map<String, String> unsupported(
			Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders,
			ProtocolLibraryProvider library
	) {
		Map<String, String> unsupported = new LinkedHashMap<>();
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders) {
			String reason = unsupportedReason(provider, library);
			if (reason != null) unsupported.put(provider.descriptor().getId(), reason);
		}

		return unsupported;
	}

	private static @Nullable String unsupportedReason(
			ProtocolPlayerCapabilityProvider<?> provider,
			ProtocolLibraryProvider library
	) {
		Set<Class<?>> missing = new LinkedHashSet<>(provider.requiredProtocols());
		missing.removeAll(library.protocols());
		if (!missing.isEmpty()) {
			String names = missing.stream().map(Class::getSimpleName).collect(Collectors.joining(", "));
			return "requires " + names + ", which the players of the " + library.id() + " library do not speak";
		}
		if (provider.workerBacked() && library.protocols().isEmpty())
			return "the players of the " + library.id() + " library run no worker extensions";

		return null;
	}

	/**
	 * Drops each protocol factory that cannot apply to the library when an applicable factory provides its
	 * capability, so the two do not compete.
	 */
	private static List<ProtocolPlayerCapabilityProvider<?>> withoutYielding(
			Collection<? extends PlayerCapabilityProvider<?>> providers,
			Collection<? extends ProtocolPlayerCapabilityProvider<?>> protocolProviders,
			Map<String, String> unsupported
	) {
		Set<Class<?>> applicable = new LinkedHashSet<>();
		for (PlayerCapabilityProvider<?> provider : providers) applicable.add(provider.capability());
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders)
			if (!unsupported.containsKey(provider.descriptor().getId())) applicable.add(provider.capability());

		List<ProtocolPlayerCapabilityProvider<?>> selected = new ArrayList<>();
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders) {
			boolean yields = unsupported.containsKey(provider.descriptor().getId())
					&& applicable.contains(provider.capability());
			if (!yields) selected.add(provider);
		}

		return selected;
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
	 * Protocol factories receive the player's channel and library services; shared factories need only the
	 * player lifetime and its connection. All matching factories share one dependency graph. Protocol factories
	 * that require a protocol the library does not speak, worker-backed factories of a player without a native
	 * worker, and factories whose capability the player's worker does not install are skipped with their reason,
	 * together with their dependents.
	 *
	 * @param player library-owned player
	 * @param metadata presentation labels of the player, or {@code null}
	 * @param connection how the player connects, with its target process named
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return extensible simulated player
	 */
	public @NotNull SimulatedPlayer compose(
			@NotNull ProtocolPlayer player,
			@Nullable PresentationMetadata metadata,
			@NotNull PlayerConnection connection,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	) {
		return new CapabilitySimulatedPlayer(player, metadata, runtime, unavailable(player), connection, onDestroyed);
	}

	private Map<String, String> unavailable(ProtocolPlayer player) {
		Map<String, String> skipped = new LinkedHashMap<>(unsupported);
		Optional<ProtocolChannel> channel = player.channel();
		if (channel.isEmpty()) {
			for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders)
				if (provider.workerBacked())
					skipped.putIfAbsent(provider.descriptor().getId(), "the players of the " + player.libraryId()
							+ " library run no worker extensions");

			return skipped;
		}

		Set<String> installed = channel.get().installedCapabilities();
		Map<String, String> reported = channel.get().unavailableCapabilities();
		for (ProtocolPlayerCapabilityProvider<?> provider : protocolProviders) {
			String id = provider.descriptor().getId();
			if (installed.contains(id) || skipped.containsKey(id)) continue;

			String reason = reported.get(id);
			skipped.put(id, reason == null ? "not installed by the " + player.libraryId() + " worker" : reason);
		}

		return skipped;
	}
}
