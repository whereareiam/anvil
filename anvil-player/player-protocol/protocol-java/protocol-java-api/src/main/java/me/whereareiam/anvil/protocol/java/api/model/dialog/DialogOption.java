package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One option of a single-option dialog input.
 */
@Value
public class DialogOption {
	/**
	 * Option ID, which is the input's value while the option is selected.
	 */
	@NotNull String id;
	/**
	 * Text the option is shown with as plain text; its ID when it has none.
	 */
	@NotNull String display;
	/**
	 * Whether the option is selected when the dialog opens.
	 */
	boolean initial;
}
