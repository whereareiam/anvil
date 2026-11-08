package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.click.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.hover.TextHover;
import me.whereareiam.anvil.protocol.api.type.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The style one {@link Text} component sets, as the server sent it. What it leaves unset, a {@code null} member or a
 * decoration missing from {@link #getDecorations()}, the component inherits from its parent; {@link SpanStyle} is the
 * style with everything inherited resolved.
 *
 * <pre>{@code
 * TextStyle style = TextStyle.builder()
 *         .color(TextColor.GOLD)
 *         .decoration(TextDecoration.BOLD, true)
 *         .click(new RunCommandClick("/arbor help"))
 *         .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class TextStyle {
	/**
	 * The style of a component that sets nothing.
	 */
	public static final TextStyle NONE = TextStyle.builder().build();

	/**
	 * The text's colour, or {@code null} to inherit it.
	 */
	@Nullable TextColor color;
	/**
	 * The colour of the text's shadow, from Minecraft 1.21.4, or {@code null} to inherit it.
	 */
	@Nullable ShadowColor shadowColor;
	/**
	 * The decorations the component turns on ({@code true}) or off ({@code false}); one it does not name is inherited.
	 */
	@NotNull
	@Singular
	Map<TextDecoration, Boolean> decorations;
	/**
	 * The resource key of the text's font, such as {@code minecraft:uniform}, or {@code null} to inherit it.
	 */
	@Nullable String font;
	/**
	 * Text a shift-click inserts into the chat input, or {@code null} to inherit it.
	 */
	@Nullable String insertion;
	/**
	 * What a click on the text does, or {@code null} to inherit it.
	 */
	@Nullable TextClick click;
	/**
	 * What the game shows while the pointer is over the text, or {@code null} to inherit it.
	 */
	@Nullable TextHover hover;
}
