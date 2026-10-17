package me.whereareiam.anvil.capability.dialogs.nbt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the payload of a custom click as the vanilla client does: the click's SNBT compound with each submitted
 * input value put in under its key, a checkbox as a byte, a slider as a float and every other input as a string.
 *
 * <pre>{@code
 * Map<String, Object> payload = ClickPayload.of(DialogClick.custom("example:submit", "{source:\"dialog\"}", values));
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClickPayload {
	/**
	 * Builds the payload of a custom click.
	 *
	 * @param click custom click whose payload and values to combine
	 * @return the payload compound as an NBT tree, empty when the click has neither payload nor values
	 * @throws IllegalArgumentException when the click's payload is not an SNBT compound
	 */
	public static @NotNull Map<String, Object> of(@NotNull DialogClick click) {
		Map<String, Object> payload = click.getPayload() == null ? new LinkedHashMap<>() : Snbt.compound(click.getPayload());
		for (DialogValue value : click.getValues())
			payload.put(value.getKey(), switch (value.getType()) {
				case BOOLEAN -> (byte) (Boolean.parseBoolean(value.getValue()) ? 1 : 0);
				case NUMBER_RANGE -> Float.parseFloat(value.getValue());
				default -> value.getValue();
			});
		return payload;
	}
}
