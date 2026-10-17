package me.whereareiam.anvil.capability.dialogs.java;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Builds the payload of a custom click as the client does: the compound the action declared, or an empty one when it
 * declared none, with the value of each input put in under the input's key, replacing a declared entry with the same
 * key. The capability holds each value as the text {@code DialogInput#getInitial()} writes; it is turned into a tag
 * here.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClickPayload {
	private static final String CHECKED = "true";
	private static final String UNCHECKED = "false";

	/**
	 * Builds a click's payload.
	 *
	 * @param declared the payload or additions the action declared, or {@code null} for none
	 * @param values the input values in input order, empty for a static custom click
	 * @return the payload: a text or option as a string tag, a checkbox as a byte tag of {@code 1} or {@code 0} and a
	 * slider as a float tag
	 * @throws IllegalArgumentException when the declared payload is not a compound or a value does not fit its input's
	 *                                  type
	 */
	static @NotNull NbtCompound of(@Nullable NbtTag declared, @NotNull List<DialogValue> values) {
		NbtCompound payload = declared == null ? NbtCompound.EMPTY : compound(declared);
		for (DialogValue value : values) payload = payload.with(value.getKey(), tag(value));

		return payload;
	}

	private static NbtCompound compound(NbtTag declared) {
		if (declared instanceof NbtCompound compound) return compound;

		throw new IllegalArgumentException("The custom click's payload " + declared + " is not a compound");
	}

	private static NbtTag tag(DialogValue value) {
		return switch (value.getType()) {
			case TEXT, SINGLE_OPTION -> new NbtString(value.getValue());
			case BOOLEAN -> NbtByte.of(checkbox(value));
			case NUMBER_RANGE -> new NbtFloat(slider(value));
			case OTHER ->
					throw new IllegalArgumentException("Input " + value.getKey() + " has a type Anvil does not know");
		};
	}

	/**
	 * Parses a checkbox's value, which is {@code true} or {@code false}.
	 *
	 * @throws IllegalArgumentException for any other text
	 */
	private static boolean checkbox(DialogValue value) {
		if (value.getValue().equals(CHECKED)) return true;
		if (value.getValue().equals(UNCHECKED)) return false;

		throw new IllegalArgumentException("Input " + value.getKey() + " holds true or false, not " + value.getValue());
	}

	/**
	 * Parses a slider's value, a decimal number such as {@code 2.5} or {@code 3}.
	 *
	 * @throws IllegalArgumentException when the text is no number
	 */
	private static float slider(DialogValue value) {
		try {
			return Float.parseFloat(value.getValue());
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("Input " + value.getKey() + " holds a number, not " + value.getValue(),
					failure);
		}
	}
}
