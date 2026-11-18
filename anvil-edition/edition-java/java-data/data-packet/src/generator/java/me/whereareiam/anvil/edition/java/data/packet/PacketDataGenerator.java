package me.whereareiam.anvil.edition.java.data.packet;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.edition.java.data.packet.generator.GenerationRequest;
import me.whereareiam.anvil.edition.java.data.packet.generator.PacketMappings;
import me.whereareiam.anvil.edition.java.data.packet.model.PacketDataSource;
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
 * Generates the packet data this module ships, at build time: the registry of each pinned protocol, read from its
 * minecraft-data {@code protocol.json}, and the index of every supported protocol, written through
 * {@link PacketDataCodec} as {@link JavaPacketData} reads them. It lives in this package to construct registries and
 * the index as the runtime does.
 *
 * <p>The build runs it with one argument, the {@link GenerationRequest} as JSON.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PacketDataGenerator {
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Generates the data a request asks for.
	 *
	 * @param arguments the request as JSON, alone
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when a protocol file breaks the expected shape
	 */
	public static void main(String @NotNull [] arguments) throws IOException {
		if (arguments.length != 1)
			throw new IllegalArgumentException("The packet data generator takes one argument, its request as JSON");

		generate(JSON.readValue(arguments[0], GenerationRequest.class));
	}

	/**
	 * Generates the data a request asks for.
	 *
	 * @param request the request
	 * @throws IOException when a file cannot be read or written
	 * @throws IllegalStateException when a protocol file breaks the expected shape
	 */
	public static void generate(@NotNull GenerationRequest request) throws IOException {
		Path output = clean(request.getOutput());
		Set<Integer> protocols = new TreeSet<>();
		for (GenerationRequest.PinnedProtocol pinned : request.getProtocols()) {
			PacketRegistry registry = registry(request.getRelease(), pinned);
			try (OutputStream file = Files.newOutputStream(output.resolve(JavaPacketData.registryResource(pinned.getProtocol())))) {
				PacketDataCodec.writeRegistry(registry, file);
			}
			protocols.add(pinned.getProtocol());
		}

		Map<Integer, String> missing = new TreeMap<>();
		GenerationRequest.Release release = request.getRelease();
		for (int protocol : request.getMissing())
			missing.put(protocol, release.getRepository() + " " + release.getVersion() + " has no protocol file for it");
		try (OutputStream file = Files.newOutputStream(output.resolve(JavaPacketData.indexResource()))) {
			PacketDataCodec.writeIndex(new JavaPacketData(protocols, missing), file);
		}
	}

	private static PacketRegistry registry(GenerationRequest.Release release, GenerationRequest.PinnedProtocol pinned)
			throws IOException {
		GenerationRequest.PinnedFile file = pinned.getFiles().getPackets();
		String context = "minecraft-data " + file.getPath() + " for protocol " + pinned.getProtocol();
		PacketDataSource source = PacketDataSource.builder()
				.repository(release.getRepository())
				.version(release.getVersion())
				.commit(release.getCommit())
				.protocol(file.getPath())
				.build();

		return new PacketRegistry(pinned.getProtocol(), source,
				PacketMappings.clientbound(context, JSON.readTree(file.getFile().toFile())));
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
