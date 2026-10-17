package me.whereareiam.anvil.capability.dialogs.nbt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtByte;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtString;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtTag;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.Snbt;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the payload of a custom click as the vanilla client does: the click's SNBT compound with each submitted
 * input value put in under its key, a checkbox as a byte, a slider as a float and every other input as a string.
 *
 * <pre>{@code
 * NbtCompound payload = ClickPayload.of(DialogClick.custom("example:submit", "{source:\"dialog\"}", values));
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClickPayload {
	/**
	 * Builds the payload of a custom click.
	 *
	 * @param click custom click whose payload and values to combine
	 * @return the payload compound, empty when the click has neither payload nor values
	 * @throws IllegalArgumentException when the click's payload is not an SNBT compound
	 */
	public static @NotNull NbtCompound of(@NotNull DialogClick click) {
		NbtCompound.NbtCompoundBuilder payload = click.getPayload() == null
				? NbtCompound.builder()
				: Snbt.readCompound(click.getPayload()).toBuilder();
		for (DialogValue value : click.getValues()) payload.entry(value.getKey(), tag(value));
		return payload.build();
	}

	private static NbtTag tag(DialogValue value) {
		return switch (value.getType()) {
			case BOOLEAN -> NbtByte.of(Boolean.parseBoolean(value.getValue()));
			case NUMBER_RANGE -> NbtFloat.of(Float.parseFloat(value.getValue()));
			default -> NbtString.of(value.getValue());
		};
	}
}
