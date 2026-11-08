package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.type.NbtSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Values read from NBT, which the server normally resolves before it sends the text.
 */
@Value
public class NbtContent implements TextContent {
	/**
	 * The NBT path read, such as {@code Items[0].id}.
	 */
	@NotNull String path;
	/**
	 * Whether the values read are text components themselves.
	 */
	boolean interpret;
	/**
	 * The text between two values, or {@code null} for the game's default, from Minecraft 1.17.
	 */
	@Nullable Text separator;
	/**
	 * Where the NBT comes from.
	 */
	@NotNull NbtSource source;
	/**
	 * What holds the NBT: a block position such as {@code ~ ~1 ~}, an entity selector, or a storage key.
	 */
	@NotNull String target;
}
