package me.whereareiam.anvil.protocol.api.model.text.hover;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Shows text, drawn on its own without the style of the text it is over.
 */
@Value
public class ShowTextHover implements TextHover {
	/**
	 * The text shown.
	 */
	@NotNull Text text;
}
