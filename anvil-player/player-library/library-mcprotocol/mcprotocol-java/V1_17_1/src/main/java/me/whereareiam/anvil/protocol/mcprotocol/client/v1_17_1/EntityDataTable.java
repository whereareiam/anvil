package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;

import java.util.Map;

/**
 * The entity data indexes of the MCProtocolLib 1.17.1 release that carry a value the protocol models, by entity type.
 * Minecraft 1.17 added the ticks an entity has been frozen at index 7, so dropped items, item frames and glow item
 * frames, thrown items, eyes of ender, fireballs and fireworks keep the item they show at 8.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityDataTable {
	private static final Map<Integer, EntityDataField> ITEM_AT_8 = Map.of(8, EntityDataField.SHOWN_ITEM);

	/**
	 * The release's layout.
	 */
	static final EntityDataLayout<EntityType> LAYOUT = new EntityDataLayout<>(Map.ofEntries(
			Map.entry(EntityType.ITEM, ITEM_AT_8),
			Map.entry(EntityType.ITEM_FRAME, ITEM_AT_8),
			Map.entry(EntityType.GLOW_ITEM_FRAME, ITEM_AT_8),
			Map.entry(EntityType.SNOWBALL, ITEM_AT_8),
			Map.entry(EntityType.THROWN_EGG, ITEM_AT_8),
			Map.entry(EntityType.THROWN_ENDERPEARL, ITEM_AT_8),
			Map.entry(EntityType.THROWN_EXP_BOTTLE, ITEM_AT_8),
			Map.entry(EntityType.THROWN_POTION, ITEM_AT_8),
			Map.entry(EntityType.EYE_OF_ENDER, ITEM_AT_8),
			Map.entry(EntityType.FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.SMALL_FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.FIREWORK_ROCKET, ITEM_AT_8)
	));
}
