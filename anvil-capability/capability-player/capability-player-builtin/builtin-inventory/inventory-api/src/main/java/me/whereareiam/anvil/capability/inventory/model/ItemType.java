package me.whereareiam.anvil.capability.inventory.model;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of item a stack is.
 */
@Value
public class ItemType {
	/**
	 * Registry key of the item, such as {@code minecraft:emerald}, or {@code null} when Anvil ships no item data for
	 * the player's protocol.
	 */
	@Nullable String identifier;
	/**
	 * Numeric ID of the item in the release's item registry, which differs between Minecraft versions.
	 */
	int protocolId;
}
