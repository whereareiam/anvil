package me.whereareiam.anvil.protocol.java.block;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.block.generator.GenerationRequest;
import me.whereareiam.anvil.protocol.java.block.generator.VanillaBlockReader;
import me.whereareiam.anvil.protocol.java.block.model.BlockDataSource;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Generates the block data this module ships, at build time: the registry of each protocol, read from the block data
 * the physics harness wrote from that release's vanilla client ({@code vanilla/<protocol>.blocks}), and the index of
 * every protocol, written through {@link BlockDataCodec} as {@link JavaBlockData} reads them. It lives in this package
 * to construct registries and the index as the runtime does.
 *
 * <p>The build runs it with one argument, the {@link GenerationRequest} as JSON. A file must be named after the
 * protocol it holds, and no protocol may come twice.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockDataGenerator {
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Generates the data a request asks for.
	 *
	 * @param arguments the request as JSON, alone
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when the data breaks a rule
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
	 * @throws IllegalStateException when the data breaks a rule
	 */
	public static void generate(@NotNull GenerationRequest request) throws IOException {
		Path output = clean(request.getOutput());
		Set<Integer> protocols = new TreeSet<>();
		for (Path file : request.getBlocks()) {
			BlockRegistry registry = registry(VanillaBlockReader.read(file));
			if (!file.getFileName().toString().equals(registry.protocol() + ".blocks"))
				throw new IllegalStateException(file.getFileName() + " holds the block data of protocol " + registry.protocol());
			if (!protocols.add(registry.protocol()))
				throw new IllegalStateException("Protocol " + registry.protocol() + " has block data twice");

			try (OutputStream stream = Files.newOutputStream(output.resolve(JavaBlockData.registryResource(registry.protocol())))) {
				BlockDataCodec.writeRegistry(registry, stream);
			}
		}

		try (OutputStream stream = Files.newOutputStream(output.resolve(JavaBlockData.indexResource()))) {
			BlockDataCodec.writeIndex(new JavaBlockData(protocols, Map.of()), stream);
		}
	}

	/**
	 * Builds the registry of the blocks a file holds.
	 *
	 * @param read the file's blocks and states
	 * @return the registry
	 * @throws IllegalArgumentException when the blocks or states are not numbered as chunk packets need
	 */
	static @NotNull BlockRegistry registry(@NotNull VanillaBlockReader.Read read) {
		return new BlockRegistry(read.getProtocol(), new BlockDataSource(read.getMinecraft()), read.getBlocks(),
				read.getStates().toArray(BlockState[]::new));
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
