package me.whereareiam.anvil.capability.entities.packet.model.data;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The text of a text display, flattened to plain text like a chat message.
 */
@Value
public class DisplayText implements EntityData {
	@NotNull String text;
}
