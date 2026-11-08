package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The colour of a {@link TextSpan} as red, green and blue. The game's sixteen named colours are constants; a server
 * may send any other colour by its value. A named colour and the same value sent as a hexadecimal colour are equal,
 * because the game draws them alike.
 *
 * <pre>{@code
 * assertEquals(TextColor.GOLD, span.getStyle().getColor());
 * assertEquals("#ffaa00", TextColor.GOLD.hex());
 * }</pre>
 */
@Value
public class TextColor {
	public static final TextColor BLACK = new TextColor(0x000000);
	public static final TextColor DARK_BLUE = new TextColor(0x0000aa);
	public static final TextColor DARK_GREEN = new TextColor(0x00aa00);
	public static final TextColor DARK_AQUA = new TextColor(0x00aaaa);
	public static final TextColor DARK_RED = new TextColor(0xaa0000);
	public static final TextColor DARK_PURPLE = new TextColor(0xaa00aa);
	public static final TextColor GOLD = new TextColor(0xffaa00);
	public static final TextColor GRAY = new TextColor(0xaaaaaa);
	public static final TextColor DARK_GRAY = new TextColor(0x555555);
	public static final TextColor BLUE = new TextColor(0x5555ff);
	public static final TextColor GREEN = new TextColor(0x55ff55);
	public static final TextColor AQUA = new TextColor(0x55ffff);
	public static final TextColor RED = new TextColor(0xff5555);
	public static final TextColor LIGHT_PURPLE = new TextColor(0xff55ff);
	public static final TextColor YELLOW = new TextColor(0xffff55);
	public static final TextColor WHITE = new TextColor(0xffffff);

	/**
	 * The colour as {@code 0xRRGGBB}.
	 */
	int value;

	/**
	 * Creates a colour from its red, green and blue.
	 *
	 * @param value the colour as {@code 0xRRGGBB}
	 * @throws IllegalArgumentException when the value has bits beyond 24
	 */
	public TextColor(int value) {
		if ((value & ~0xffffff) != 0)
			throw new IllegalArgumentException("A text colour is 0xRRGGBB, not 0x" + Integer.toHexString(value));
		this.value = value;
	}

	/**
	 * Returns the colour as the game writes it in text components.
	 *
	 * @return the colour as {@code #rrggbb}
	 */
	public @NotNull String hex() {
		return String.format(Locale.ROOT, "#%06x", value);
	}
}
