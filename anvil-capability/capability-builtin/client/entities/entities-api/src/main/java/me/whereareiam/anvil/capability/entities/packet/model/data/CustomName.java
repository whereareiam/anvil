package me.whereareiam.anvil.capability.entities.packet.model.data;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The entity's custom name, flattened to plain text like a chat message, or null when the server removed it.
 */
@Value
public class CustomName implements EntityData {
	@Nullable String name;
}
