package me.whereareiam.anvil.protocol.java.api.model.dialog;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The value of one dialog input that a dynamic custom click puts into its payload under the input's key: the text of
 * a text field or the ID of a selected option, the state of a checkbox, or the number of a slider. Exactly one of the
 * typed fields is set; the factories create each kind.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DialogInputValue {
	/**
	 * Key of the input, under which the value is put into the payload.
	 */
	@NotNull String key;
	/**
	 * The text of a text field or the selected option's ID, put as a string tag, or {@code null} for another kind.
	 */
	@Nullable String text;
	/**
	 * Whether a checkbox is checked, put as a byte tag of {@code 1} or {@code 0}, or {@code null} for another kind.
	 */
	@Nullable Boolean checked;
	/**
	 * The number of a slider, put as a float tag, or {@code null} for another kind.
	 */
	@Nullable Float number;

	/**
	 * Creates the value of a text field or a single-option input.
	 *
	 * @param key input key
	 * @param text the text or the selected option's ID
	 * @return the value
	 */
	public static @NotNull DialogInputValue text(@NotNull String key, @NotNull String text) {
		return new DialogInputValue(key, text, null, null);
	}

	/**
	 * Creates the value of a checkbox.
	 *
	 * @param key input key
	 * @param checked whether the checkbox is checked
	 * @return the value
	 */
	public static @NotNull DialogInputValue checkbox(@NotNull String key, boolean checked) {
		return new DialogInputValue(key, null, checked, null);
	}

	/**
	 * Creates the value of a slider.
	 *
	 * @param key input key
	 * @param number the slider's number
	 * @return the value
	 */
	public static @NotNull DialogInputValue slider(@NotNull String key, float number) {
		return new DialogInputValue(key, null, null, number);
	}
}
