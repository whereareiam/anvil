package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.DialogInputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One input of a dialog, with the game's defaults for the fields the server left out. Fields that do not belong to
 * the input's type are {@code null} or empty.
 */
@Value
@Builder
public class DialogInput {
	/**
	 * Key the input's value is submitted under.
	 */
	@NotNull String key;
	/**
	 * The input's type.
	 */
	@NotNull DialogInputType type;
	/**
	 * Label shown with the input as plain text, empty when it has none.
	 */
	@NotNull String label;
	/**
	 * Value the input holds when the dialog opens: the text, {@code true} or {@code false}, the selected option's ID,
	 * or the number, written without a fraction when it is whole; empty for an input of another type.
	 */
	@NotNull String initial;
	/**
	 * Longest text a text input accepts, {@code 32} by default.
	 */
	@Nullable Integer maxLength;
	/**
	 * Text a checkbox puts into a command template while checked, {@code true} by default.
	 */
	@Nullable String onTrue;
	/**
	 * Text a checkbox puts into a command template while unchecked, {@code false} by default.
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
