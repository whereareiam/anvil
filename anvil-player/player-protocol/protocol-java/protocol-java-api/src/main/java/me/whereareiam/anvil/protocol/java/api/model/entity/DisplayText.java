package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The text a text display shows; releases before Minecraft 1.19.4 have no text displays.
 */
@Value
public class DisplayText implements EntityDataValue {
	/**
	 * The text as plain text.
	 */
	@NotNull String text;
}
