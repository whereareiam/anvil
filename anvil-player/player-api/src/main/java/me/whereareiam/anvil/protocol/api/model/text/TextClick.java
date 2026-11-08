package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.type.ClickAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a click on a {@link TextSpan} does: the action and what it acts on.
 *
 * <pre>{@code
 * assertEquals(new TextClick(ClickAction.RUN_COMMAND, "/arbor help"), span.getStyle().getClick());
 * }</pre>
 */
@Value
public class TextClick {
	/**
	 * The action a click takes.
	 */
	@NotNull ClickAction action;
	/**
	 * What the action acts on: the URL, file, command or text of the text actions, the page number of
	 * {@link ClickAction#CHANGE_PAGE}, the identifier of {@link ClickAction#CUSTOM}, or {@code null} for
	 * {@link ClickAction#SHOW_DIALOG}, whose dialog is no text.
	 */
	@Nullable String value;
}
