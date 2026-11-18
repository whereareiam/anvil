package me.whereareiam.anvil.edition.java.data.item.generator;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * What the build asks the generator for, as one JSON argument: the directory to write into, the pinned minecraft-data
 * release, the item file Gradle resolved for each pinned protocol, and the supported protocols it has no data for.
 * The build's {@code MinecraftDataGeneratorArguments} writes it.
 */
@Value
@Builder
@Jacksonized
public class GenerationRequest {
	/**
	 * The resource directory of {@code JavaItemData}'s package, which the generator replaces.
	 */
	@NotNull Path output;
	/**
	 * The pinned minecraft-data release.
	 */
	@NotNull Release release;
	/**
	 * Each protocol with item data, and its file.
	 */
	@Singular
	@NotNull List<PinnedProtocol> protocols;
	/**
	 * The supported protocols the release has no item data for.
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
	 * One protocol's pinned file.
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
		 * Its files, by the fields of {@code item-data.toml} that pin them.
		 */
		@NotNull Files files;
	}

	/**
	 * One protocol's {@code items.json}.
	 */
	@Value
	@Builder
	@Jacksonized
	public static class Files {
		/**
		 * Its {@code items.json}.
		 */
		@NotNull PinnedFile items;
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
		 * Its path in the repository, such as {@code data/pc/1.21.11/items.json}.
		 */
		@NotNull String path;
	}
}
