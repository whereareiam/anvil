package me.whereareiam.anvil.protocol.api.library;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Validated registry of protocol libraries discovered from an application class loader.
 *
 * <p>Several libraries may be installed together. The registry only indexes them by identifier;
 * choosing a library for a player belongs to player management, which ranks the libraries by the
 * support level of their releases for that player's Minecraft version.</p>
 *
 * <pre>{@code
 * ProtocolLibraryRegistry libraries = ProtocolLibraryRegistry.discover();
 * ProtocolLibraryProvider mcprotocol = libraries.require("mcprotocol");
 * }</pre>
 */
public final class ProtocolLibraryRegistry {
	private final Map<String, ProtocolLibraryProvider> providers;

	/**
	 * Creates a registry from explicit providers, primarily for embedding and contract tests.
	 *
	 * @param providers provider implementations
	 * @throws IllegalArgumentException when an identifier is blank or duplicated
	 */
	public ProtocolLibraryRegistry(@NotNull Collection<? extends ProtocolLibraryProvider> providers) {
		Map<String, ProtocolLibraryProvider> indexed = new LinkedHashMap<>();
		for (ProtocolLibraryProvider provider : providers) {
			if (provider.id().isBlank())
				throw new IllegalArgumentException("Protocol library ID must not be blank: " + provider.getClass().getName());

			ProtocolLibraryProvider duplicate = indexed.putIfAbsent(provider.id(), provider);
			if (duplicate != null)
				throw new IllegalArgumentException("Duplicate protocol library ID '" + provider.id() + "': "
						+ duplicate.getClass().getName() + " and " + provider.getClass().getName());
		}

		this.providers = Collections.unmodifiableMap(indexed);
	}

	/**
	 * Discovers libraries from the current context class loader, falling back to this class's loader
	 * when the context loader provides none.
	 *
	 * @return validated library registry
	 */
	public static @NotNull ProtocolLibraryRegistry discover() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		List<ProtocolLibraryProvider> providers = loader == null ? List.of() : load(loader);
		if (providers.isEmpty()) providers = load(ProtocolLibraryRegistry.class.getClassLoader());

		return new ProtocolLibraryRegistry(providers);
	}

	/**
	 * Returns installed library identifiers in discovery order.
	 *
	 * @return immutable library identifiers
	 */
	public @NotNull Collection<String> ids() {
		return providers.keySet();
	}

	/**
	 * Returns installed library providers in discovery order.
	 *
	 * @return immutable providers
	 */
	public @NotNull Collection<ProtocolLibraryProvider> all() {
		return providers.values();
	}

	/**
	 * Finds an installed library without creating it.
	 *
	 * @param id library identifier
	 * @return matching provider, or empty when the library is not installed
	 */
	public @NotNull Optional<ProtocolLibraryProvider> find(@NotNull String id) {
		return Optional.ofNullable(providers.get(id));
	}

	/**
	 * Returns an installed library without creating it.
	 *
	 * @param id library identifier
	 * @return matching provider
	 * @throws IllegalArgumentException when the library is not installed; the message names the installed libraries
	 */
	public @NotNull ProtocolLibraryProvider require(@NotNull String id) {
		return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown protocol library '" + id
				+ "'. Installed: " + providers.keySet()));
	}

	private static List<ProtocolLibraryProvider> load(ClassLoader loader) {
		return ServiceLoader.load(ProtocolLibraryProvider.class, loader).stream()
				.map(ServiceLoader.Provider::get)
				.toList();
	}
}
