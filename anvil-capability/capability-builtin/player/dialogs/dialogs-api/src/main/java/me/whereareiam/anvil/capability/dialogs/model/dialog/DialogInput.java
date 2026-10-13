package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One input of a dialog. Fields that do not belong to the input's type are {@code null} or empty.
 */
@Value
@Builder
public class DialogInput {
	/**
	 * Key the input's value is submitted under.
	 */
	@NotNull String key;
	@NotNull DialogInputType type;
	/**
	 * Label shown with the input, flattened to plain text.
	 */
	@NotNull String label;
	/**
	 * Value the input holds when the dialog opens, written as {@link me.whereareiam.anvil.capability.dialogs.Dialogs#press}
	 * accepts it: the text, {@code true} or {@code false}, the selected option's ID, or the number.
	 */
	@NotNull String initial;
	/**
	 * Longest text a text input accepts.
	 */
	@Nullable Integer maxLength;
	/**
	 * Text a checkbox puts into a command template while checked.
	 */
	@Nullable String onTrue;
	/**
	 * Text a checkbox puts into a command template while unchecked.
	 */
	@Nullable String onFalse;
	/**
	 * Options of a single-option input, in display order.
	 */
	@NotNull @Singular List<DialogOption> options;
	/**
	 * Lowest value of a slider.
	 */
	@Nullable Float start;
	/**
	 * Highest value of a slider.
	 */
	@Nullable Float end;
	/**
	 * Step between the values of a slider, or {@code null} when the slider moves freely.
	 */
	@Nullable Float step;
}
