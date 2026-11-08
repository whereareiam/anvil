package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The colour of a text's shadow, from Minecraft 1.21.4, as alpha, red, green and blue.
 *
 * <pre>{@code
 * assertEquals(new ShadowColor(0x80000000), span.getStyle().getShadowColor());
 * }</pre>
 */
@Value
public class ShadowColor {
	/**
	 * The colour as {@code 0xAARRGGBB}.
	 */
	int value;

	/**
	 * Returns the colour as the game writes it in text components.
	 *
	 * @return the colour as {@code #aarrggbb}
	 */
	public @NotNull String hex() {
		return String.format(Locale.ROOT, "#%08x", value);
	}
}
