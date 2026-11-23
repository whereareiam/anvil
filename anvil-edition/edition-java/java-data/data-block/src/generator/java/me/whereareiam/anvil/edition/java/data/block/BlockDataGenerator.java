package me.whereareiam.anvil.edition.java.data.block;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.data.block.generator.GenerationRequest;
import me.whereareiam.anvil.edition.java.data.block.generator.RegistryAssembly;
import me.whereareiam.anvil.edition.java.data.block.generator.facts.BlockFacts;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataBlock;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataShapes;
import me.whereareiam.anvil.edition.java.data.block.model.BlockDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Generates the block data this module ships, at build time: the registry of each pinned protocol, assembled from its
 * minecraft-data files and {@code block-facts.toml}, and the index of every supported protocol, written through
 * {@link BlockDataCodec} as {@link JavaBlockData} reads them. It lives in this package to construct registries and
 * the index as the runtime does.
 *
 * <p>The build runs it with one argument, the {@link GenerationRequest} as JSON. Every block {@code block-facts.toml}
 * names must exist in at least one protocol, so a misspelt name fails instead of being ignored.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockDataGenerator {
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Generates the data a request asks for.
	 *
	 * @param arguments the request as JSON, alone
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when the data or the facts break a rule
	 */
	public static void main(String @NotNull [] arguments) throws IOException {
		if (arguments.length != 1)
			throw new IllegalArgumentException("The block data generator takes one argument, its request as JSON");

		generate(JSON.readValue(arguments[0], GenerationRequest.class));
	}

	/**
	 * Generates the data a request asks for.
	 *
	 * @param request the request
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when the data or the facts break a rule
	 */
	public static void generate(@NotNull GenerationRequest request) throws IOException {
		BlockFacts facts = BlockFacts.read(request.facts());
		Path output = clean(request.output());
		Set<String> names = new HashSet<>();
		Set<Integer> protocols = new TreeSet<>();
		for (GenerationRequest.PinnedProtocol pinned : request.protocols()) {
			BlockRegistry registry = registry(request.release(), pinned, facts, names);
			Path resource = output.resolve(JavaBlockData.registryResource(pinned.protocol()));
			try (OutputStream file = Files.newOutputStream(resource)) {
				BlockDataCodec.writeRegistry(registry, file);
			}
			protocols.add(pinned.protocol());
		}

		facts.names().stream().filter(name -> !names.contains(name)).sorted().findFirst().ifPresent(name -> {
			throw new IllegalStateException("block-facts.toml names " + name + ", which no protocol of block-data.toml has");
		});
		Map<Integer, String> missing = new TreeMap<>();
		GenerationRequest.Release release = request.release();
		for (int protocol : request.missing())
			missing.put(protocol, release.repository() + " " + release.version() + " has no block data for it");
		try (OutputStream file = Files.newOutputStream(output.resolve(JavaBlockData.indexResource()))) {
			BlockDataCodec.writeIndex(new JavaBlockData(protocols, missing), file);
		}
	}

	private static BlockRegistry registry(
			GenerationRequest.Release release,
			GenerationRequest.PinnedProtocol pinned,
			BlockFacts facts,
			Set<String> names
	) throws IOException {
		String context = "minecraft-data " + pinned.files().blocks().path() + " for protocol " + pinned.protocol();
		List<MinecraftDataBlock> blocks = JSON.readValue(pinned.files().blocks().file().toFile(), new TypeReference<>() {
		});
		MinecraftDataShapes shapes = JSON.readValue(pinned.files().collisionShapes().file().toFile(), MinecraftDataShapes.class);
		RegistryAssembly.Assembled assembled = RegistryAssembly.assemble(context, blocks, shapes, facts);
		names.addAll(assembled.names());

		BlockDataSource source = BlockDataSource.builder()
				.repository(release.repository())
				.version(release.version())
				.commit(release.commit())
				.blocks(pinned.files().blocks().path())
				.collisionShapes(pinned.files().collisionShapes().path())
				.build();
		return new BlockRegistry(pinned.protocol(), source, assembled.blocks(), assembled.states());
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
