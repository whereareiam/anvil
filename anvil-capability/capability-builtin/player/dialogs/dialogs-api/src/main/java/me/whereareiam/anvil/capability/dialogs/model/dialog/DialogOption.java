package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One option of a {@link me.whereareiam.anvil.capability.dialogs.type.DialogInputType#SINGLE_OPTION} input.
 */
@Value
public class DialogOption {
	/**
	 * Option ID, which is the input's value while the option is selected.
	 */
	@NotNull String id;
	/**
	 * Text the option is shown with, flattened to plain text.
	 */
	@NotNull String display;
	/**
	 * Whether the option is selected when the dialog opens.
	 */
	boolean initial;
}
