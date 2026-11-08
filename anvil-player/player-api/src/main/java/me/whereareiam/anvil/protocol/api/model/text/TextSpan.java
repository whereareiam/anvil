package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One run of {@link StyledText}: text the game draws in one style.
 */
@Value
public class TextSpan {
	/**
	 * The run's text, with its line breaks.
	 */
	@NotNull String text;
	/**
	 * The style the game draws the text in, with everything it inherits resolved.
	 */
	@NotNull TextStyle style;
}
