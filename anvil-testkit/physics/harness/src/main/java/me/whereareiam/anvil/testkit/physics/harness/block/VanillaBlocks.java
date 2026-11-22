package me.whereareiam.anvil.testkit.physics.harness.block;

import lombok.Value;

import java.util.List;

/**
 * The blocks of one vanilla client: every block in block ID order, their states numbered from {@code 0} without gaps.
 */
@Value
public class VanillaBlocks {
	/**
	 * The Minecraft version the client reports.
	 */
	String minecraft;

	/**
	 * The protocol number the client speaks.
	 */
	int protocol;

	List<VanillaBlock> blocks;
}
