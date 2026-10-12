package me.whereareiam.anvil.protocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.segment.SegmentDescriptor;
import me.whereareiam.anvil.protocol.worker.segment.SegmentLinkage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The segments on a worker's class path, the result of their linkage self-check against the loaded release, and
 * the adapters they provide.
 *
 * <p>The host selects one segment per owner before launch. The worker verifies each selected segment's linkage
 * manifest with its own class loader, then hands out adapters on request: the adapter of a port is the one
 * implementation that a selected segment declares in {@code META-INF/services/<port>}. A port that no segment
 * provides, a providing segment that does not link, or more than one declared implementation makes the adapter
 * unavailable with that reason. The library's client port is obtained the same way, from its client segment, and
 * must be available, or the worker cannot start. Segments of other libraries are ignored.</p>
 */
public final class WorkerSegments {
	private static final String SERVICES = "META-INF/services/";

	private final ClassLoader loader;
	private final WorkerLibrary library;
	private final MinecraftVersion version;
	private final Map<String, Segment> segments = new LinkedHashMap<>();
	private final Map<Class<?>, Object> adapters = new ConcurrentHashMap<>();

	private WorkerSegments(@NotNull ClassLoader loader, @NotNull WorkerLibrary library, @NotNull MinecraftVersion version) {
		this.loader = loader;
		this.library = library;
		this.version = version;
	}

	/**
	 * Reads and verifies every segment of a library visible to a class loader.
	 *
	 * @param loader loader that defines the worker's classes
	 * @param library library whose segments are verified
	 * @param version key version of the loaded release, used in messages
	 * @param warnings receiver of one line per segment that does not link
	 * @return verified segments
	 * @throws IllegalStateException when two segments share an owner
	 */
	static @NotNull WorkerSegments verify(
			@NotNull ClassLoader loader,
			@NotNull WorkerLibrary library,
			@NotNull MinecraftVersion version,
			@NotNull Consumer<String> warnings
	) {
		WorkerSegments verified = new WorkerSegments(loader, library, version);
		Map<String, MinecraftVersion> owners = new LinkedHashMap<>();
		for (String root : roots(loader, SegmentDescriptor.PROPERTIES)) {
			SegmentDescriptor descriptor = read(root + SegmentDescriptor.PROPERTIES, SegmentDescriptor::read);
			if (!descriptor.getLibrary().equals(library.getId())) continue;
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
	 * @throws NativeAdapterUnavailableException when no selected segment provides the port, the providing segment
	 * does not link, more than one implementation is declared, or the adapter cannot be created
	 */
	public <P> @NotNull P adapter(@NotNull Class<P> port) {
		Object adapter = adapters.computeIfAbsent(port, this::create);
		if (adapter instanceof Unavailable unavailable) throw new NativeAdapterUnavailableException(unavailable.reason());

		return port.cast(adapter);
	}

	/**
	 * Loads the client of the client segment, the adapter of the library's client port.
	 *
	 * @param port the library's client port, {@link WorkerLibrary#getClientPort()}
	 * @param <C> client port type
	 * @return the client segment's adapter
	 * @throws IllegalStateException when the client adapter is unavailable
	 */
	public <C> @NotNull C client(@NotNull Class<C> port) {
		try {
			return adapter(port);
		} catch (NativeAdapterUnavailableException unavailable) {
			throw new IllegalStateException("Cannot load the " + library.getName() + " client: " + unavailable.getMessage(), unavailable);
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

	private Object create(Class<?> port) {
		List<Declaration> declarations = new ArrayList<>();
		String resource = SERVICES + port.getName();
		for (String root : roots(loader, resource))
			for (String provider : read(root + resource, (input, location) -> providerNames(input)))
				declarations.add(new Declaration(root, provider));

		if (declarations.isEmpty())
			return new Unavailable("no " + library.getId() + " segment selected for Minecraft " + version
					+ " provides an adapter for " + port.getName());
		if (declarations.size() > 1)
			return new Unavailable("more than one adapter for " + port.getName() + " is on the worker class path: "
					+ declarations.stream().map(this::describe).toList());

		Declaration declaration = declarations.getFirst();
		Segment segment = segments.get(declaration.root());
		if (segment == null)
			return new Unavailable("the adapter " + declaration.provider() + " for " + port.getName() + " is declared outside a segment, by "
					+ declaration.root());
		if (segment.linkageFailure() != null) return new Unavailable(unlinked(segment));

		try {
			Class<?> type = Class.forName(declaration.provider(), true, loader);
			if (!port.isAssignableFrom(type))
				return new Unavailable("the adapter " + declaration.provider() + " of " + describe(segment) + " does not implement " + port.getName());

			return type.getConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
			return new Unavailable("could not create the adapter " + declaration.provider() + " of " + describe(segment) + ": "
					+ failure.getClass().getSimpleName() + ": " + failure.getMessage());
		}
	}

	private String unlinked(Segment segment) {
		return describe(segment) + " does not link against the " + library.getName() + " release for Minecraft " + version + ": "
				+ segment.linkageFailure();
	}

	private String describe(Declaration declaration) {
		Segment segment = segments.get(declaration.root());
		return declaration.provider() + " in " + (segment == null ? declaration.root() : describe(segment));
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

	private static List<String> providerNames(InputStream input) {
		try {
			List<String> names = new ArrayList<>();
			for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
				String name = line.split("#", 2)[0].trim();
				if (!name.isEmpty()) names.add(name);
			}
			return names;
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read a service declaration", exception);
		}
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
	 * One provider name declared for a port, and the class-path root declaring it.
	 */
	private record Declaration(String root, String provider) {
	}

	/**
	 * Why a port has no adapter; remembered so every player gets the same reason.
	 */
	private record Unavailable(String reason) {
	}
}
