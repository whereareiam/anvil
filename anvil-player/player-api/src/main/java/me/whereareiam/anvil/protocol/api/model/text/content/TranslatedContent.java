package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A translation the client looks up in its language files, with the arguments it puts in, such as the vanilla
 * {@code multiplayer.player.joined} with the player's name.
 */
@Value
public class TranslatedContent implements TextContent {
	/**
	 * The translation key, such as {@code multiplayer.player.joined}.
	 */
	@NotNull String key;
	/**
	 * Text the client shows when its language files lack the key, from Minecraft 1.19.4, or {@code null}.
	 */
	@Nullable String fallback;
	/**
	 * The arguments the translation puts in, in order, each inheriting the translation's style.
	 */
	@NotNull List<Text> arguments;

	/**
	 * Creates a translation with a copy of its arguments.
	 *
	 * @param key the translation key
	 * @param fallback text shown when the key is missing, or {@code null}
	 * @param arguments the arguments, in order
	 */
	public TranslatedContent(@NotNull String key, @Nullable String fallback, @NotNull List<Text> arguments) {
		this.key = key;
		this.fallback = fallback;
		this.arguments = List.copyOf(arguments);
	}
}
