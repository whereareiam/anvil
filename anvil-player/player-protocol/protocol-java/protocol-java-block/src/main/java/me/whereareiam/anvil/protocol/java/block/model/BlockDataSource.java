package me.whereareiam.anvil.protocol.java.block.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Where a protocol's block data comes from: the vanilla client it was read from, by its own registries, classes and
 * methods, by the physics harness's {@code generateVanillaData}.
 */
@Value
public class BlockDataSource {
	/**
	 * The Minecraft version of the vanilla client, such as {@code 1.21.11}, which {@code vanilla-clients.toml} pins.
	 */
	@NotNull String minecraft;
}
