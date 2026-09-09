package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates and composes capability providers for one kind of owner.
 * Each composition receives its own capability instances and cleanup lifetime.
 *
 * @param <C> capabilities belonging to the owner
 * @param <X> owner-specific provider context
 */
public final class CapabilityRuntime<C extends Capability, X extends CapabilityContext<C>> {
	private final List<CapabilityProvider<? extends C, X>> providers;

	/**
	 * Validates identities, owner types, and dependencies before ordering providers.
	 *
	 * @param ownerType capability marker belonging to this owner
	 * @param providers providers for this owner
	 */
	public CapabilityRuntime(
			@NotNull Class<C> ownerType,
			@NotNull Collection<? extends CapabilityProvider<? extends C, X>> providers
	) {
		this.providers = validateAndOrder(ownerType, providers);
	}

	/**
	 * Returns immutable provider descriptors in dependency order.
	 *
	 * @return ordered provider descriptors
	 */
	public @NotNull List<CapabilityDescriptor> providers() {
		return providers.stream().map(CapabilityProvider::descriptor).toList();
	}

	/**
	 * Creates a capability set, adapting a restricted dependency context for each provider.
	 * Registered cleanup is rolled back if either context or capability creation fails.
	 *
	 * @param ownerDescription owner identity used in failure diagnostics
	 * @param contextFactory supplies the owner's services around each restricted context
	 * @return composed capabilities and their cleanup lifetime
	 */
	public @NotNull CapabilitySet<C> compose(
			@NotNull String ownerDescription,
			@NotNull Function<CapabilityContext<C>, X> contextFactory
	) {
		CapabilitySet<C> capabilities = new CapabilitySet<>(ownerDescription);
		try {
			for (CapabilityProvider<? extends C, X> provider : providers)
				create(provider, capabilities, ownerDescription, contextFactory);
		} catch (RuntimeException | Error failure) {
			try {
				capabilities.close();
			} catch (RuntimeException | Error cleanupFailure) {
				if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
			}

			throw failure;
		}

		return capabilities;
	}

	private <T extends C> void create(
			CapabilityProvider<T, X> provider,
			CapabilitySet<C> capabilities,
			String ownerDescription,
			Function<CapabilityContext<C>, X> contextFactory
	) {
		CapabilityDescriptor descriptor = provider.descriptor();
		try {
			X context = contextFactory.apply(capabilities.context(descriptor.getId(), descriptor.getRequiredCapabilities()));
			T capability = provider.create(context);
			if (!provider.capability().isInstance(capability))
				throw new CapabilityException("Provider '" + descriptor.getId() + "' for " + ownerDescription
						+ " returned " + (capability == null ? "null" : capability.getClass().getName())
						+ " instead of " + provider.capability().getName());
			capabilities.add(provider.capability(), capability);
		} catch (CapabilityException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new CapabilityException("Could not create provider '" + descriptor.getId()
					+ "' for " + ownerDescription, exception);
		}
	}

	private List<CapabilityProvider<? extends C, X>> validateAndOrder(
			Class<C> ownerType,
			Collection<? extends CapabilityProvider<? extends C, X>> candidates
	) {
		Map<String, CapabilityProvider<? extends C, X>> byId = new LinkedHashMap<>();
		Map<Class<? extends Capability>, String> byCapability = new LinkedHashMap<>();
		for (CapabilityProvider<? extends C, X> provider : candidates) {
			CapabilityDescriptor descriptor = provider.descriptor();
			if (descriptor.getId().isBlank())
				throw new CapabilityException("Anvil provider ID must not be blank: " + provider.getClass().getName());
			if (descriptor.getApiVersion() != CapabilityDescriptor.CURRENT_API_VERSION)
				throw new CapabilityException("Provider '" + descriptor.getId() + "' targets API "
						+ descriptor.getApiVersion() + " but Anvil supports " + CapabilityDescriptor.CURRENT_API_VERSION);
			validateOwnerType(ownerType, provider.capability(), descriptor.getId());
			for (Class<? extends Capability> dependency : descriptor.getRequiredCapabilities())
				validateOwnerType(ownerType, dependency, descriptor.getId());

			CapabilityProvider<? extends C, X> duplicate = byId.putIfAbsent(descriptor.getId(), provider);
			if (duplicate != null)
				throw new CapabilityException("Duplicate Anvil provider ID '" + descriptor.getId() + "': "
						+ duplicate.getClass().getName() + " and " + provider.getClass().getName());
			String capabilityOwner = byCapability.putIfAbsent(provider.capability(), descriptor.getId());
			if (capabilityOwner != null)
				throw new CapabilityException("Capability " + provider.capability().getName()
						+ " is provided by both '" + capabilityOwner + "' and '" + descriptor.getId() + "'");
		}

		for (CapabilityProvider<? extends C, X> provider : byId.values()) {
			Set<Class<? extends Capability>> missing = new LinkedHashSet<>(provider.descriptor().getRequiredCapabilities());
			missing.removeAll(byCapability.keySet());
			if (!missing.isEmpty())
				throw new CapabilityException("Provider '" + provider.descriptor().getId()
						+ "' requires missing capabilities " + missing.stream().map(Class::getName).toList());
		}

		List<CapabilityProvider<? extends C, X>> ordered = new ArrayList<>();
		Set<String> complete = new LinkedHashSet<>();
		Set<String> active = new LinkedHashSet<>();
		Deque<String> path = new ArrayDeque<>();
		for (String id : byId.keySet())
			visit(id, byId, byCapability, complete, active, path, ordered);

		return List.copyOf(ordered);
	}

	private void validateOwnerType(Class<C> ownerType, Class<? extends Capability> type, String providerId) {
		if (!ownerType.isAssignableFrom(type))
			throw new CapabilityException("Provider '" + providerId + "' declares capability " + type.getName()
					+ " outside owner type " + ownerType.getName());
	}

	private void visit(
			String id,
			Map<String, CapabilityProvider<? extends C, X>> providers,
			Map<Class<? extends Capability>, String> capabilityOwners,
			Set<String> complete,
			Set<String> active,
			Deque<String> path,
			List<CapabilityProvider<? extends C, X>> ordered
	) {
		if (complete.contains(id)) return;
		if (!active.add(id)) {
			path.addLast(id);
			throw new CapabilityException("Cyclic Anvil capability-provider dependency: " + String.join(" -> ", path));
		}

		path.addLast(id);
		CapabilityProvider<? extends C, X> provider = providers.get(id);
		for (Class<? extends Capability> dependency : provider.descriptor().getRequiredCapabilities())
			visit(capabilityOwners.get(dependency), providers, capabilityOwners, complete, active, path, ordered);

		path.removeLast();
		active.remove(id);
		complete.add(id);
		ordered.add(provider);
	}
}
