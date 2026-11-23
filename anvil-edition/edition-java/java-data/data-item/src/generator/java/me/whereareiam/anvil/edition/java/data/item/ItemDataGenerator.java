package me.whereareiam.anvil.edition.java.data.item;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.data.item.generator.GenerationRequest;
import me.whereareiam.anvil.edition.java.data.item.generator.ItemList;
import me.whereareiam.anvil.edition.java.data.item.model.ItemDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Generates the item data this module ships, at build time: the registry of each pinned protocol, read from its
 * minecraft-data {@code items.json}, and the index of every supported protocol, written through {@link ItemDataCodec}
 * as {@link JavaItemData} reads them. It lives in this package to construct registries and the index as the runtime
 * does.
 *
 * <p>The build runs it with one argument, the {@link GenerationRequest} as JSON.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemDataGenerator {
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Generates the data a request asks for.
	 *
	 * @param arguments the request as JSON, alone
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when an item file breaks the expected shape
	 */
	public static void main(String @NotNull [] arguments) throws IOException {
		if (arguments.length != 1)
			throw new IllegalArgumentException("The item data generator takes one argument, its request as JSON");

		generate(JSON.readValue(arguments[0], GenerationRequest.class));
	}

	/**
	 * Generates the data a request asks for.
	 *
	 * @param request the request
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when an item file breaks the expected shape
	 */
	public static void generate(@NotNull GenerationRequest request) throws IOException {
		Path output = clean(request.output());
		Set<Integer> protocols = new TreeSet<>();
		for (GenerationRequest.PinnedProtocol pinned : request.protocols()) {
			ItemRegistry registry = registry(request.release(), pinned);
			try (OutputStream file = Files.newOutputStream(output.resolve(JavaItemData.registryResource(pinned.protocol())))) {
				ItemDataCodec.writeRegistry(registry, file);
			}
			protocols.add(pinned.protocol());
		}

		Map<Integer, String> missing = new TreeMap<>();
		GenerationRequest.Release release = request.release();
		for (int protocol : request.missing())
			missing.put(protocol, release.repository() + " " + release.version() + " has no item file for it");
		try (OutputStream file = Files.newOutputStream(output.resolve(JavaItemData.indexResource()))) {
			ItemDataCodec.writeIndex(new JavaItemData(protocols, missing), file);
		}
	}

	private static ItemRegistry registry(GenerationRequest.Release release, GenerationRequest.PinnedProtocol pinned)
			throws IOException {
		GenerationRequest.PinnedFile file = pinned.files().items();
		String context = "minecraft-data " + file.path() + " for protocol " + pinned.protocol();
		ItemDataSource source = ItemDataSource.builder()
				.repository(release.repository())
				.version(release.version())
				.commit(release.commit())
				.items(file.path())
				.build();

		return new ItemRegistry(pinned.protocol(), source, ItemList.keys(context, JSON.readTree(file.file().toFile())));
	}

	/**
	 * Empties the output directory, so no data of a protocol the request no longer lists remains.
	 */
	private static Path clean(Path output) throws IOException {
		if (Files.exists(output))
			try (Stream<Path> paths = Files.walk(output)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
					Files.delete(path);
			}

		return Files.createDirectories(output);
	}
}
