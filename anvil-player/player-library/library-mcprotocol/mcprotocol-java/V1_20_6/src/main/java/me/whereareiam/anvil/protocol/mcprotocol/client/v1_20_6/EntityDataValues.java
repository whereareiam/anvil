package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomName;
import me.whereareiam.anvil.protocol.java.api.model.entity.CustomNameVisible;
import me.whereareiam.anvil.protocol.java.api.model.entity.DisplayText;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import me.whereareiam.anvil.protocol.java.api.model.entity.ShownItem;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Reads the entity data values the protocol models from the values the MCProtocolLib releases from 1.20.5 on decode,
 * which keep their kinds: the shared flags as a byte, the custom name as an optional component, the shown item as a
 * stack and a display's text as a component. Each release's table says which index carries which value.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntityDataValues {
	/**
	 * Reads the value an index carries; a value of another kind than the table names, which only a server of another
	 * release sends, is left out.
	 *
	 * @param field what the index carries for the entity's type
	 * @param value the value the library decoded
	 * @return the protocol's value, or {@code null} when the value is of another kind
	 */
	public static @Nullable EntityDataValue value(@NotNull EntityDataField field, @Nullable Object value) {
		return switch (field) {
			case SHARED_FLAGS -> value instanceof Byte flags ? SharedFlags.of(flags) : null;
			case CUSTOM_NAME -> value instanceof Optional<?> name ? customName(name) : null;
			case CUSTOM_NAME_VISIBLE -> value instanceof Boolean visible ? new CustomNameVisible(visible) : null;
			case SHOWN_ITEM -> value == null || value instanceof ItemStack
					? new ShownItem(ValueTranslator.item((ItemStack) value))
					: null;
			case DISPLAY_TEXT -> value instanceof Component text ? new DisplayText(ValueTranslator.plain(text)) : null;
		};
	}

	private static @Nullable CustomName customName(Optional<?> name) {
		if (name.isEmpty()) return new CustomName(null);
		if (!(name.get() instanceof Component text)) return null;

		return new CustomName(ValueTranslator.plain(text));
	}
}
