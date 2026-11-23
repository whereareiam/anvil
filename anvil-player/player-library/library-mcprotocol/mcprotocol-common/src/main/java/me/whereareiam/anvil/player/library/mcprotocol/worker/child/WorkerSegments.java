package me.whereareiam.anvil.player.library.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.player.library.mcprotocol.segment.SegmentDescriptor;
import me.whereareiam.anvil.player.library.mcprotocol.segment.SegmentLinkage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The segments on a worker's class path, the result of their linkage self-check against the loaded release, and
 * the adapters they provide.
 *
 * <p>The host selects one segment per owner before launch. The worker verifies each selected segment's linkage
 * manifest with its own class loader, then hands out adapters on request: the adapter of a port is its one service
 * provider that {@link ServiceLoader} finds, which must come from the class path root of a selected segment. The
 * provider is created only once its segment is known to link. A port without a provider, a provider outside a
 * segment or in a segment that does not link, more than one provider, or a provider that cannot be loaded or
 * created makes the adapter unavailable with that reason. The client segment's {@link McProtocolClient} is obtained
 * the same way and must be available, or the worker cannot start.</p>
 */
final class WorkerSegments {
	private final ClassLoader loader;
	private final MinecraftVersion version;
	private final Map<String, Segment> segments = new LinkedHashMap<>();
	private final Map<Class<?>, Object> adapters = new ConcurrentHashMap<>();

	private WorkerSegments(@NotNull ClassLoader loader, @NotNull MinecraftVersion version) {
		this.loader = loader;
		this.version = version;
	}

	/**
	 * Reads and verifies every MCProtocolLib segment visible to a class loader.
	 *
	 * @param loader loader that defines the worker's classes
	 * @param version key version of the loaded release, used in messages
	 * @param warnings receiver of one line per segment that does not link
	 * @return verified segments
	 * @throws IllegalStateException when two segments share an owner
	 */
	static @NotNull WorkerSegments verify(
			@NotNull ClassLoader loader,
			@NotNull MinecraftVersion version,
			@NotNull Consumer<String> warnings
	) {
		WorkerSegments verified = new WorkerSegments(loader, version);
		Map<String, MinecraftVersion> owners = new LinkedHashMap<>();
		for (String root : roots(loader, SegmentDescriptor.PROPERTIES)) {
			SegmentDescriptor descriptor = read(root + SegmentDescriptor.PROPERTIES, SegmentDescriptor::read);
			if (!descriptor.getLibrary().equals(McProtocolClient.LIBRARY_ID)) continue;
			if (owners.putIfAbsent(descriptor.getOwner(), descriptor.getSince()) != null)
				throw new IllegalStateException("The worker class path holds more than one segment of " + descriptor.getOwner());

			Optional<String> failure = read(root + SegmentDescriptor.LINKAGE, SegmentLinkage::read).firstFailure(loader);
			Segment segment = new Segment(descriptor, failure.orElse(null));
			verified.segments.put(root, segment);
			if (failure.isPresent()) warnings.accept("[Anvil] Warning: " + verified.unlinked(segment) + "; its adapters are unavailable");
		}

		return verified;
	}

	/**
	 * Returns the adapter of a port, creating it on first use. Adapters are stateless and shared by every player.
	 *
	 * @param port port interface
	 * @param <P> port type
	 * @return the adapter of the segment selected for the release
	 * @throws ProtocolUnavailableException when no selected segment provides the port, the providing segment
	 * does not link, more than one implementation is declared, or the adapter cannot be created
	 */
	<P> @NotNull P adapter(@NotNull Class<P> port) {
		Object adapter = adapters.computeIfAbsent(port, this::create);
		if (adapter instanceof Unavailable unavailable) throw new ProtocolUnavailableException(unavailable.reason());

		return port.cast(adapter);
	}

	/**
	 * Loads the client of the client segment.
	 *
	 * @return client typed for the opaque sessions the worker passes back to it
	 * @throws IllegalStateException when the client adapter is unavailable
	 */
	@SuppressWarnings("unchecked")
	@NotNull McProtocolClient<Object> client() {
		try {
			return (McProtocolClient<Object>) adapter(McProtocolClient.class);
		} catch (ProtocolUnavailableException unavailable) {
			throw new IllegalStateException("Cannot load the MCProtocolLib client: " + unavailable.getMessage(), unavailable);
		}
	}

	/**
	 * Returns the start version of each selected segment by owner.
	 *
	 * @return immutable start versions keyed by owner
	 */
	@NotNull Map<String, String> selected() {
		Map<String, String> versions = new LinkedHashMap<>();
		segments.values().forEach(segment -> versions.put(segment.descriptor().getOwner(), segment.descriptor().getSince().toString()));
		return Map.copyOf(versions);
	}

	private <P> Object create(Class<P> port) {
		List<Provided<P>> provided = new ArrayList<>();
		try {
			ServiceLoader.load(port, loader).stream().forEach(provider -> provided.add(new Provided<>(root(provider.type()), provider)));
		} catch (ServiceConfigurationError | LinkageError failure) {
			return new Unavailable("could not load the adapters for " + port.getName() + ": " + failure.getClass().getSimpleName()
					+ ": " + failure.getMessage());
		}

		if (provided.isEmpty())
			return new Unavailable("no " + McProtocolClient.LIBRARY_ID + " segment selected for Minecraft " + version
					+ " provides an adapter for " + port.getName());
		if (provided.size() > 1)
			return new Unavailable("more than one adapter for " + port.getName() + " is on the worker class path: "
					+ provided.stream().map(this::describe).toList());

		Provided<P> adapter = provided.getFirst();
		Segment segment = segments.get(adapter.root());
		if (segment == null)
			return new Unavailable("the adapter " + adapter.name() + " for " + port.getName() + " is declared outside a segment, by "
					+ adapter.origin());
		if (segment.linkageFailure() != null) return new Unavailable(unlinked(segment));

		try {
			return adapter.provider().get();
		} catch (ServiceConfigurationError | LinkageError | RuntimeException failure) {
			Throwable cause = failure instanceof ServiceConfigurationError && failure.getCause() != null ? failure.getCause() : failure;
			return new Unavailable("could not create the adapter " + adapter.name() + " of " + describe(segment) + ": "
					+ cause.getClass().getSimpleName() + ": " + cause.getMessage());
		}
	}

	private String unlinked(Segment segment) {
		return describe(segment) + " does not link against the MCProtocolLib release for Minecraft " + version + ": "
				+ segment.linkageFailure();
	}

	private String describe(Provided<?> adapter) {
		Segment segment = segments.get(adapter.root());
		return adapter.name() + " in " + (segment == null ? adapter.origin() : describe(segment));
	}

	private static String describe(Segment segment) {
		return "segment " + segment.descriptor().getOwner() + " " + segment.descriptor().getSince();
	}

	private static List<String> roots(ClassLoader loader, String resource) {
		try {
			List<String> roots = new ArrayList<>();
			for (URL found : Collections.list(loader.getResources(resource))) {
				String location = found.toString();
				roots.add(location.substring(0, location.length() - resource.length()));
			}
			return roots;
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not list " + resource + " on the worker class path", exception);
		}
	}

	/**
	 * Names the class path root a class was loaded from, in the form {@link #roots} names segment roots, or
	 * {@code null} when its loader has no class file for it.
	 */
	private static @Nullable String root(Class<?> type) {
		String resource = type.getName().replace('.', '/') + ".class";
		URL location = type.getClassLoader().getResource(resource);
		if (location == null) return null;

		String found = location.toString();
		return found.substring(0, found.length() - resource.length());
	}

	private static <T> T read(String location, BiFunction<InputStream, String, T> reader) {
		try (InputStream input = URI.create(location).toURL().openStream()) {
			return reader.apply(input, location);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read " + location, exception);
		}
	}

	/**
	 * A selected segment and the reason it does not link, or {@code null} when it links.
	 */
	private record Segment(SegmentDescriptor descriptor, @Nullable String linkageFailure) {
	}

	/**
	 * One service provider found for a port, and the class path root its class was loaded from.
	 */
	private record Provided<P>(@Nullable String root, ServiceLoader.Provider<P> provider) {
		String name() {
			return provider.type().getName();
		}

		String origin() {
			return root == null ? "a class loader without its class file" : root;
		}
	}

	/**
	 * Why a port has no adapter; remembered so every player gets the same reason.
	 */
	private record Unavailable(String reason) {
	}
}
