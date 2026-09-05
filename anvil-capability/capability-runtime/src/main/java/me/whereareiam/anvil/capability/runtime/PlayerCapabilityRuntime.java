package me.whereareiam.anvil.capability.runtime;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Discovers, validates, orders, and composes dependency-provided player providers.
 */
public final class PlayerCapabilityRuntime implements ProtocolPlayerComposer {
	private final List<PlayerCapabilityProvider<?>> providers;

	/**
	 * Discovers providers from the current thread context class loader.
	 *
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover() {
		return discover((String) null);
	}

	/**
	 * Discovers providers compatible with one selected protocol backend.
	 *
	 * @param protocolId selected protocol-provider ID, or {@code null} for all providers
	 * @return validated provider runtime
	 */
	public static @NotNull PlayerCapabilityRuntime discover(@Nullable String protocolId) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return discover(loader == null ? PlayerCapabilityRuntime.class.getClassLoader() : loader, protocolId);
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
		List<PlayerCapabilityProvider<?>> providers = new ArrayList<>();
		for (PlayerCapabilityProvider<?> provider : ServiceLoader.load(PlayerCapabilityProvider.class, loader))
			if (protocolId == null || provider.descriptor().getSupportedProtocolIds().isEmpty()
					|| provider.descriptor().getSupportedProtocolIds().contains(protocolId))
				providers.add(provider);
		return new PlayerCapabilityRuntime(providers);
	}

	/**
	 * Creates a runtime from explicit providers, primarily for embedding and contract tests.
	 *
	 * @param providers capability providers
	 */
	public PlayerCapabilityRuntime(@NotNull Collection<? extends PlayerCapabilityProvider<?>> providers) {
		this.providers = validateAndOrder(providers);
	}

	/**
	 * Returns provider descriptors in dependency order.
	 *
	 * @return immutable descriptor list
	 */
	public @NotNull List<CapabilityDescriptor> providers() {
		return providers.stream().map(PlayerCapabilityProvider::descriptor).toList();
	}

	/**
	 * Composes all discovered capabilities around one backend-owned player.
	 *
	 * @param player backend-owned protocol client
	 * @param services scenario-scoped services visible to providers
	 * @param onDestroyed callback invoked after permanent destruction
	 * @return extensible simulated player
	 */
	@Override
	public @NotNull SimulatedPlayer compose(
			@NotNull ProtocolPlayer player,
			@NotNull Collection<?> services,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	) {
		return new CapabilitySimulatedPlayer(player, providers, services, onDestroyed);
	}

	/**
	 * Compatibility alias for the runtime's historical composition method.
	 *
	 * @param player backend-owned protocol client
	 * @param services scenario-scoped services
	 * @param onDestroyed destruction callback
	 * @return composed simulated player
	 */
	public @NotNull SimulatedPlayer create(
			@NotNull ProtocolPlayer player,
			@NotNull Collection<?> services,
			@NotNull Consumer<SimulatedPlayer> onDestroyed
	) {
		return compose(player, services, onDestroyed);
	}

	private List<PlayerCapabilityProvider<?>> validateAndOrder(Collection<? extends PlayerCapabilityProvider<?>> candidates) {
		Map<String, PlayerCapabilityProvider<?>> byId = new LinkedHashMap<>();
		Map<Class<? extends PlayerCapability>, String> byCapability = new LinkedHashMap<>();
		for (PlayerCapabilityProvider<?> provider : candidates) {
			CapabilityDescriptor descriptor = provider.descriptor();
			if (descriptor.getId().isBlank())
				throw new CapabilityException("Anvil provider ID must not be blank: " + provider.getClass().getName());
			if (descriptor.getApiVersion() != CapabilityDescriptor.CURRENT_API_VERSION)
				throw new CapabilityException("Provider '" + descriptor.getId() + "' targets API "
						+ descriptor.getApiVersion() + " but Anvil supports " + CapabilityDescriptor.CURRENT_API_VERSION);
			PlayerCapabilityProvider<?> duplicate = byId.putIfAbsent(descriptor.getId(), provider);
			if (duplicate != null)
				throw new CapabilityException("Duplicate Anvil provider ID '" + descriptor.getId() + "': "
						+ duplicate.getClass().getName() + " and " + provider.getClass().getName());
			String capabilityOwner = byCapability.putIfAbsent(provider.capability(), descriptor.getId());
			if (capabilityOwner != null)
				throw new CapabilityException("Capability " + provider.capability().getName()
						+ " is provided by both '" + capabilityOwner + "' and '" + descriptor.getId() + "'");
		}

		for (PlayerCapabilityProvider<?> provider : byId.values()) {
			Set<Class<? extends PlayerCapability>> missing =
					new LinkedHashSet<>(provider.descriptor().getRequiredCapabilities());
			missing.removeAll(byCapability.keySet());
			if (!missing.isEmpty())
				throw new CapabilityException("Provider '" + provider.descriptor().getId()
						+ "' requires missing capabilities "
						+ missing.stream().map(Class::getName).toList());
		}

		List<PlayerCapabilityProvider<?>> ordered = new ArrayList<>();
		Set<String> complete = new LinkedHashSet<>();
		Set<String> active = new LinkedHashSet<>();
		Deque<String> path = new ArrayDeque<>();
		for (String id : byId.keySet())
			visit(id, byId, byCapability, complete, active, path, ordered);
		return List.copyOf(ordered);
	}

	private void visit(
			String id,
			Map<String, PlayerCapabilityProvider<?>> providers,
			Map<Class<? extends PlayerCapability>, String> capabilityOwners,
			Set<String> complete,
			Set<String> active,
			Deque<String> path,
			List<PlayerCapabilityProvider<?>> ordered
	) {
		if (complete.contains(id))
			return;
		if (!active.add(id)) {
			path.addLast(id);
			throw new CapabilityException("Cyclic Anvil capability-provider dependency: "
					+ String.join(" -> ", path));
		}
		path.addLast(id);
		PlayerCapabilityProvider<?> provider = providers.get(id);
		for (Class<? extends PlayerCapability> capability : provider.descriptor().getRequiredCapabilities()) {
			String dependency = capabilityOwners.get(capability);
			if (dependency == null)
				throw new CapabilityException("Capability " + capability.getName()
						+ " required by provider '" + provider.descriptor().getId()
						+ "' is not available");
			visit(dependency, providers, capabilityOwners, complete, active, path, ordered);
		}
		path.removeLast();
		active.remove(id);
		complete.add(id);
		ordered.add(provider);
	}
}
