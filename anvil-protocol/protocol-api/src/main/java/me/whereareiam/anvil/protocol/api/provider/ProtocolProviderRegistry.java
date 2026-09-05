package me.whereareiam.anvil.protocol.api.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Validated registry of protocol providers discovered from an application class loader.
 *
 * <p>Applications may install several providers and select one by its stable ID. Provider
 * implementations remain independent of the launcher and own their worker/runtime dependencies.</p>
 */
public final class ProtocolProviderRegistry {
	private final Map<String, ProtocolProvider> providers;

	/**
	 * Discovers providers from the current context class loader.
	 *
	 * @return validated provider registry
	 */
	public static @NotNull ProtocolProviderRegistry discover() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		List<ProtocolProvider> providers = loader == null
				? List.of()
				: ServiceLoader.load(ProtocolProvider.class, loader).stream()
				.map(ServiceLoader.Provider::get)
				.toList();
		if (providers.isEmpty())
			providers = ServiceLoader.load(ProtocolProvider.class, ProtocolProviderRegistry.class.getClassLoader()).stream()
					.map(ServiceLoader.Provider::get)
					.toList();
		return new ProtocolProviderRegistry(providers);
	}

	/**
	 * Creates a registry from explicit providers, primarily for embedding and contract tests.
	 *
	 * @param providers provider implementations
	 */
	public ProtocolProviderRegistry(@NotNull Collection<? extends ProtocolProvider> providers) {
		Map<String, ProtocolProvider> indexed = new LinkedHashMap<>();
		for (ProtocolProvider provider : providers) {
			if (provider.id().isBlank())
				throw new IllegalArgumentException("Protocol provider ID must not be blank: "
						+ provider.getClass().getName());
			ProtocolProvider duplicate = indexed.putIfAbsent(provider.id(), provider);
			if (duplicate != null)
				throw new IllegalArgumentException("Duplicate protocol provider ID '" + provider.id() + "': "
						+ duplicate.getClass().getName() + " and " + provider.getClass().getName());
		}
		this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
	}

	/**
	 * Returns installed provider IDs in discovery order.
	 *
	 * @return immutable provider IDs
	 */
	public @NotNull Collection<String> ids() {
		return providers.keySet();
	}

	/**
	 * Creates the selected backend.
	 *
	 * @param id selected provider ID, or {@code null} when exactly one provider is installed
	 * @param cacheDirectory private Anvil cache root
	 * @return selected protocol backend
	 */
	public @NotNull ProtocolBackend create(@Nullable String id, @NotNull Path cacheDirectory) {
		return select(id).create(cacheDirectory);
	}

	/**
	 * Selects a provider without creating its backend or accessing authentication state.
	 * Use the resolved provider ID for capability discovery and related tooling.
	 *
	 * @param id explicit provider ID, or {@code null} when exactly one provider is installed
	 * @return selected provider
	 * @throws IllegalStateException when automatic selection is empty or ambiguous
	 * @throws IllegalArgumentException when an explicit provider ID is unknown
	 */
	public @NotNull ProtocolProvider select(@Nullable String id) {
		String selected = id;
		if (selected == null) {
			if (providers.isEmpty())
				throw new IllegalStateException("No protocol providers are installed");
			if (providers.size() != 1)
				throw new IllegalStateException("Multiple protocol providers are installed: " + providers.keySet()
						+ "; select one explicitly");
			selected = providers.keySet().iterator().next();
		}
		ProtocolProvider provider = providers.get(selected);
		if (provider == null)
			throw new IllegalArgumentException("Unknown protocol provider '" + selected + "'. Installed: "
					+ providers.keySet());
		return provider;
	}
}
