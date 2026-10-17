package me.whereareiam.anvil.capability.entities.packet.model.data;

import lombok.Value;
import me.whereareiam.anvil.capability.entities.model.EntityItem;
import org.jetbrains.annotations.Nullable;

/**
 * The item an entity shows, or null when it shows none.
 */
@Value
public class ShownItem implements EntityData {
	@Nullable EntityItem item;
}
