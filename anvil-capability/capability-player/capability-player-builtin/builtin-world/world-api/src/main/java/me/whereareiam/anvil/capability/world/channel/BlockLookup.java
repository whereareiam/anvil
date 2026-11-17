package me.whereareiam.anvil.capability.world.channel;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The worker's answer to a read of one block: the block as the player's client knows it, or no block while the client
 * has no chunk holding it.
 */
@Value
public class BlockLookup {
	/**
	 * The block the client knows, or {@code null} while the client has no chunk holding it.
	 */
	@Nullable BlockReading reading;
}
