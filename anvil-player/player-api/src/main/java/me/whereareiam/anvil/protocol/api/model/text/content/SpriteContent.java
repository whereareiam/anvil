package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A sprite drawn inside the text, from Minecraft 1.21.9.
 */
@Value
public class SpriteContent implements TextContent {
	/**
	 * The atlas the sprite is in, such as {@code minecraft:blocks}.
	 */
	@NotNull String atlas;
	/**
	 * The sprite, such as {@code minecraft:block/stone}.
	 */
	@NotNull String sprite;
}
