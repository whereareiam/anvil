package me.whereareiam.anvil.capability.interaction.model;

import lombok.Value;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable interaction operation request shared by host and native bindings.
 */
@Value
public class BlockUse {
	int x;
	int y;
	int z;
	@NotNull BlockFace face;
	@NotNull Hand hand;
}
