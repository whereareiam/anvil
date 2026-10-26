package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The level the player is in, as the worker answers a read of it.
 */
@Value
public class KnownDimension {
	/**
	 * The level, or null before the first login of the current connection, while the player configures and once its
	 * connection ended.
	 */
	@Nullable SeenDimension dimension;
}
