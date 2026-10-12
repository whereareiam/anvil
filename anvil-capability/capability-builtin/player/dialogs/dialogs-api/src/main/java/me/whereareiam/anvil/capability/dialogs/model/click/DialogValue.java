package me.whereareiam.anvil.capability.dialogs.model.click;

import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.jetbrains.annotations.NotNull;

/**
 * The value of one input that a dynamic custom click submits.
 */
@Value
public class DialogValue {
	@NotNull String key;
	/**
	 * Type of the input, which decides the NBT type of the value: a string for text and options, a byte for a
	 * checkbox and a float for a slider.
	 */
	@NotNull DialogInputType type;
	/**
	 * The value as {@link me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput#getInitial()} writes it.
	 */
	@NotNull String value;
}
