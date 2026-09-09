package me.whereareiam.anvil.launcher.assembly;

import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads installed providers from the application's class loader for outer assembly.
 */
public final class ProviderDiscovery {
	@NotNull Map<String, PlatformProvider> platforms() {
		return all(PlatformProvider.class).stream()
				.collect(Collectors.toUnmodifiableMap(PlatformProvider::id, Function.identity()));
	}

	@NotNull Map<String, ExecutionProvider> executions(@NotNull Collection<ExecutionProvider> supplied) {
		Map<String, ExecutionProvider> result = new LinkedHashMap<>();
		all(ExecutionProvider.class).forEach(provider -> result.put(provider.id(), provider));
		supplied.forEach(provider -> result.put(provider.id(), provider));

		return Map.copyOf(result);
	}

	/**
	 * Loads the first provider without constructing unused alternatives.
	 */
	public <T> @NotNull T required(@NotNull Class<T> type, @NotNull String description) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		Optional<T> selected = context == null ? Optional.empty() : ServiceLoader.load(type, context).findFirst();

		return selected.or(() -> ServiceLoader.load(type, ProviderDiscovery.class.getClassLoader()).findFirst())
				.orElseThrow(() -> new IllegalStateException("No Anvil " + description + " is installed"));
	}

	private <T> @NotNull List<T> all(@NotNull Class<T> type) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		List<T> providers = context == null ? List.of() : load(type, context);
		if (!providers.isEmpty()) return providers;

		return load(type, ProviderDiscovery.class.getClassLoader());
	}

	private <T> @NotNull List<T> load(@NotNull Class<T> type, @NotNull ClassLoader loader) {
		return ServiceLoader.load(type, loader).stream().map(ServiceLoader.Provider::get).toList();
	}
}
