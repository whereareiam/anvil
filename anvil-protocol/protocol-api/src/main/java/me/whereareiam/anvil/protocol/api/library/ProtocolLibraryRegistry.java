package me.whereareiam.anvil.protocol.api.library;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
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
	private final List<HostBinding> hosts;

	/**
	 * Creates a registry from explicit providers, primarily for embedding and contract tests. It has no
	 * {@link #authentication(Path) account workflow}.
	 *
	 * @param providers provider implementations
	 * @throws IllegalArgumentException when an identifier is blank or duplicated
	 */
	public ProtocolLibraryRegistry(@NotNull Collection<? extends ProtocolLibraryProvider> providers) {
		this(providers, List.of());
	}

	private ProtocolLibraryRegistry(@NotNull Collection<? extends ProtocolLibraryProvider> providers, @NotNull List<HostBinding> hosts) {
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
		this.hosts = List.copyOf(hosts);
	}

	/**
	 * Discovers libraries from the current context class loader, falling back to this class's loader
	 * when the context loader provides none. Both the directly registered {@link ProtocolLibraryProvider}
	 * implementations and the libraries of every {@link ProtocolLibraryHost} are installed.
	 *
	 * @return validated library registry
	 */
	public static @NotNull ProtocolLibraryRegistry discover() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		ProtocolLibraryRegistry discovered = loader == null ? null : load(loader);
		if (discovered == null || discovered.providers.isEmpty()) discovered = load(ProtocolLibraryRegistry.class.getClassLoader());

		return discovered;
	}

	/**
	 * Returns the account workflow of the installed libraries: the one a {@link ProtocolLibraryHost} offers for the
	 * libraries it runs. Creates neither a library nor a player.
	 *
	 * @param accountsDirectory directory of the stored account files
	 * @return the account workflow, or empty when no installed host offers one
	 * @throws IllegalStateException when several installed hosts offer an account workflow, which would store
	 * accounts the others cannot read
	 */
	public @NotNull Optional<ProtocolAuthentication> authentication(@NotNull Path accountsDirectory) {
		List<ProtocolAuthentication> offered = new ArrayList<>();
		List<String> owners = new ArrayList<>();
		for (HostBinding binding : hosts) {
			Optional<ProtocolAuthentication> authentication = binding.host().authentication(binding.loader(), accountsDirectory);
			if (authentication.isEmpty()) continue;

			offered.add(authentication.get());
			owners.add(binding.host().getClass().getName());
		}

		if (offered.size() > 1)
			throw new IllegalStateException("Several installed protocol library hosts offer an account workflow: " + owners);

		return offered.stream().findFirst();
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

	private static ProtocolLibraryRegistry load(ClassLoader loader) {
		List<ProtocolLibraryProvider> providers = new ArrayList<>();
		List<HostBinding> hosts = new ArrayList<>();
		ServiceLoader.load(ProtocolLibraryProvider.class, loader).forEach(providers::add);
		for (ProtocolLibraryHost host : ServiceLoader.load(ProtocolLibraryHost.class, loader)) {
			hosts.add(new HostBinding(host, loader));
			providers.addAll(host.libraries(loader));
		}

		return new ProtocolLibraryRegistry(providers, hosts);
	}

	private record HostBinding(@NotNull ProtocolLibraryHost host, @NotNull ClassLoader loader) { }
}
