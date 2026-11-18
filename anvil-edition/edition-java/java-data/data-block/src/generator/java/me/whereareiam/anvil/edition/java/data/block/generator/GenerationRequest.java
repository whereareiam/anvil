package me.whereareiam.anvil.edition.java.data.block.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * What the build asks the generator for, as one JSON argument: the facts, the directory to write into, the pinned
 * minecraft-data release, the files Gradle resolved for each pinned protocol, and the supported protocols it has no
 * data for. The build's {@code MinecraftDataGeneratorArguments} writes it.
 */
@Value
@Builder
@Jacksonized
public class GenerationRequest {
	/**
	 * {@code block-facts.toml}.
	 */
	@NotNull Path facts;
	/**
	 * The resource directory of {@code JavaBlockData}'s package, which the generator replaces.
	 */
	@NotNull Path output;
	/**
	 * The pinned minecraft-data release.
	 */
	@NotNull Release release;
	/**
	 * Each protocol with block data, and its files.
	 */
	@Singular
	@NotNull List<PinnedProtocol> protocols;
	/**
	 * The supported protocols the release has no block data for.
	 */
	@Singular("missingProtocol")
	@NotNull List<Integer> missing;

	/**
	 * A minecraft-data release pinned to a commit.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class Release {
		/**
		 * The GitHub repository, such as {@code PrismarineJS/minecraft-data}.
		 */
		@NotNull String repository;
		/**
		 * The release, such as {@code 3.117.0}.
		 */
		@NotNull String version;
		/**
		 * The commit the release is pinned to.
		 */
		@NotNull String commit;
	}

	/**
	 * One protocol's pinned files.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class PinnedProtocol {
		/**
		 * The protocol number.
		 */
		int protocol;
		/**
		 * Its files, by the fields of {@code block-data.toml} that pin them.
		 */
		@NotNull Files files;
	}

	/**
	 * One protocol's {@code blocks.json} and {@code blockCollisionShapes.json}.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class Files {
		/**
		 * Its {@code blocks.json}.
		 */
		@NotNull PinnedFile blocks;
		/**
		 * Its {@code blockCollisionShapes.json}.
		 */
		@JsonProperty("collision-shapes")
		@NotNull PinnedFile collisionShapes;
	}

	/**
	 * A pinned minecraft-data file and where Gradle resolved it.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class PinnedFile {
		/**
		 * The resolved file, whose checksum the build matched against the pin.
		 */
		@NotNull Path file;
		/**
		 * Its path in the repository, such as {@code data/pc/1.16.2/blocks.json}.
		 */
		@NotNull String path;
	}
}
