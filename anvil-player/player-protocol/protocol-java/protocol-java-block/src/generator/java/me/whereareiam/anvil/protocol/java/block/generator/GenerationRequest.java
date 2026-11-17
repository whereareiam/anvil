package me.whereareiam.anvil.protocol.java.block.generator;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * What the build asks the generator for, as one JSON argument: the directory to write into and the block data read
 * from each release's vanilla client, one {@code <protocol>.blocks} per protocol. The build's
 * {@code BlockDataGeneratorArguments} writes it.
 */
@Value
@Builder
@Jacksonized
public class GenerationRequest {
	/**
	 * The resource directory of {@code JavaBlockData}'s package, which the generator replaces.
	 */
	@NotNull Path output;
	/**
	 * The vanilla block data files, each {@code <protocol>.blocks}.
	 */
	@Singular
	@NotNull List<Path> blocks;
}
