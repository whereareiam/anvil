package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.click.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.hover.TextHover;
import me.whereareiam.anvil.protocol.api.type.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The style the game draws one {@link TextSpan} in, with what it inherits from the components around it resolved: a
 * decoration nobody turns on is off, and a colour, font, insertion, click or hover nobody sets is {@code null}, which
 * the game draws in its defaults, such as white chat text.
 *
 * <pre>{@code
 * SpanStyle style = SpanStyle.builder().color(TextColor.GOLD).bold(true).build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class SpanStyle {
	/**
	 * The style of text nothing styles.
	 */
	public static final SpanStyle NONE = SpanStyle.builder().build();

	/**
	 * The text's colour, or {@code null} for the game's default colour of where the text is shown.
	 */
	@Nullable TextColor color;
	/**
	 * The colour of the text's shadow, or {@code null} for the game's default shadow.
	 */
	@Nullable ShadowColor shadowColor;
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
	 * What the game shows while the pointer is over the text, or {@code null} when it shows nothing.
	 */
	@Nullable TextHover hover;

	/**
	 * Returns this style with what a component sets applied over it.
	 */
	@NotNull SpanStyle inherit(@NotNull TextStyle style) {
		SpanStyleBuilder resolved = toBuilder()
				.bold(style.getDecorations().getOrDefault(TextDecoration.BOLD, bold))
				.italic(style.getDecorations().getOrDefault(TextDecoration.ITALIC, italic))
				.underlined(style.getDecorations().getOrDefault(TextDecoration.UNDERLINED, underlined))
				.strikethrough(style.getDecorations().getOrDefault(TextDecoration.STRIKETHROUGH, strikethrough))
				.obfuscated(style.getDecorations().getOrDefault(TextDecoration.OBFUSCATED, obfuscated));
		if (style.getColor() != null) resolved.color(style.getColor());
		if (style.getShadowColor() != null) resolved.shadowColor(style.getShadowColor());
		if (style.getFont() != null) resolved.font(style.getFont());
		if (style.getInsertion() != null) resolved.insertion(style.getInsertion());
		if (style.getClick() != null) resolved.click(style.getClick());
		if (style.getHover() != null) resolved.hover(style.getHover());

		return resolved.build();
	}
}
