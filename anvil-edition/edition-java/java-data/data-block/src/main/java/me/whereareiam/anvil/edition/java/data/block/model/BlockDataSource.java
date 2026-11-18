package me.whereareiam.anvil.edition.java.data.block.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Where a protocol's block data comes from: the PrismarineJS minecraft-data release, the commit it is pinned to and
 * the two files the registry is made of.
 */
@Value
@Builder
public class BlockDataSource {
	/**
	 * The GitHub repository, such as {@code PrismarineJS/minecraft-data}.
	 */
	@NotNull String repository;
	/**
	 * The minecraft-data release, such as {@code 3.117.0}.
	 */
	@NotNull String version;
	/**
	 * The commit the release is pinned to.
	 */
	@NotNull String commit;
	/**
	 * The blocks file, such as {@code data/pc/1.16.2/blocks.json}.
	 */
	@NotNull String blocks;
	/**
	 * The collision shapes file, such as {@code data/pc/1.16.1/blockCollisionShapes.json}.
	 */
	@NotNull String collisionShapes;
}
