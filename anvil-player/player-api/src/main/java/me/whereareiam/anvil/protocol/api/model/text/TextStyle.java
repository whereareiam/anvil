package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The style one {@link TextSpan} is drawn in, with what it inherits from the components around it resolved: a
 * decoration nobody sets is off, and a colour, font, insertion, click or hover nobody sets is {@code null}, which the
 * game draws in its defaults, such as white chat text.
 *
 * <pre>{@code
 * TextStyle style = TextStyle.builder().color(TextColor.GOLD).bold(true).build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class TextStyle {
	/**
	 * The style of text nothing styles.
	 */
	public static final TextStyle NONE = TextStyle.builder().build();

	/**
	 * The text's colour, or {@code null} for the game's default colour of where the text is shown.
	 */
	@Nullable TextColor color;
	/**
	 * Whether the text is bold.
	 */
	boolean bold;
	/**
	 * Whether the text is italic.
	 */
	boolean italic;
	/**
	 * Whether the text is underlined.
	 */
	boolean underlined;
	/**
	 * Whether the text is struck through.
	 */
	boolean strikethrough;
	/**
	 * Whether the text is obfuscated, drawn as changing random characters.
	 */
	boolean obfuscated;
	/**
	 * The resource key of the text's font, such as {@code minecraft:uniform}, or {@code null} for the default font.
	 */
	@Nullable String font;
	/**
	 * Text a shift-click inserts into the chat input, or {@code null} when a shift-click inserts nothing.
	 */
	@Nullable String insertion;
	/**
	 * What a click on the text does, or {@code null} when a click does nothing.
	 */
	@Nullable TextClick click;
	/**
	 * Text shown while the pointer is over the text, or {@code null} when the text shows none. Only text is modelled;
	 * an item or entity the server shows instead leaves it {@code null}.
	 */
	@Nullable StyledText hover;
}
