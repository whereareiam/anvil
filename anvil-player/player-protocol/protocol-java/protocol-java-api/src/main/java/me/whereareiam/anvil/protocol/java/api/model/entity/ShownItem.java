package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item an entity shows: a dropped item, the item in an item frame or an item display, a thrown item, an eye of
 * ender, a fireball or a firework.
 */
@Value
public class ShownItem implements EntityDataValue {
	/**
	 * The item, or {@code null} when the entity shows none.
	 */
	@Nullable ItemStack item;
}
