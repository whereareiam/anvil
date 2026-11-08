package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A player's head drawn inside the text, from Minecraft 1.21.9, by the player's name, identity or skin texture.
 */
@Value
public class PlayerHeadContent implements TextContent {
	/**
	 * The player's name, or {@code null}.
	 */
	@Nullable String name;
	/**
	 * The player's identity, or {@code null}.
	 */
	@Nullable UUID uniqueId;
	/**
	 * The skin texture, or {@code null}.
	 */
	@Nullable String texture;
	/**
	 * Whether the head is drawn with the skin's hat layer.
	 */
	boolean hat;
}
