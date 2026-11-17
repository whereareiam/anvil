package me.whereareiam.anvil.testkit.physics.harness.block;

import me.whereareiam.anvil.testkit.physics.harness.VanillaClient;
import me.whereareiam.anvil.testkit.physics.harness.VanillaOutput;

import java.nio.file.Path;

/**
 * Reads the client's blocks from its own registries and writes them as {@code <protocol>.blocks}.
 */
public final class BlockOutput implements VanillaOutput {
	private final Path directory;

	/**
	 * @param directory the directory the block data is written into
	 */
	public BlockOutput(Path directory) {
		this.directory = directory;
	}

	@Override
	public void write(VanillaClient client, String minecraft) throws Exception {
		VanillaBlocks blocks = BlockTableReader.read(minecraft, client.blocks());
		Path file = BlockDataWriter.write(directory, blocks);
		System.out.println("Wrote the block data of " + minecraft + ": " + blocks.getBlocks().size() + " blocks, protocol "
			+ blocks.getProtocol() + ", " + file.getFileName());
	}
}
