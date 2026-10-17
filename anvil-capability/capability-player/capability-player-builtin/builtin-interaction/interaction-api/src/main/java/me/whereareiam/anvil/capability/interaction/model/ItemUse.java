package me.whereareiam.anvil.capability.interaction.model;

import lombok.Value;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable interaction operation request shared by host and native bindings.
 */
@Value
public class ItemUse {
	@NotNull Hand hand;
}
