package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.Map;

/**
 * The entity data indexes of the MCProtocolLib 1.21.11 release that carry a value the protocol models, by entity type.
 * Dropped items, thrown items, eyes of ender, fireballs, fireworks and ominous item spawners keep the item they show
 * at 8. Item frames keep their direction at 8, as every hanging entity does in this release, and their item at 9.
 * A display keeps fifteen values from index 8, so a text display keeps its text and an item display its item at 23.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityDataTable {
	private static final Map<Integer, EntityDataField> ITEM_AT_8 = Map.of(8, EntityDataField.SHOWN_ITEM);
	private static final Map<Integer, EntityDataField> ITEM_AT_9 = Map.of(9, EntityDataField.SHOWN_ITEM);
	private static final Map<Integer, EntityDataField> DISPLAY_AT_23 = Map.of(23, EntityDataField.SHOWN_ITEM);
	private static final Map<Integer, EntityDataField> TEXT_AT_23 = Map.of(23, EntityDataField.DISPLAY_TEXT);

	/**
	 * The release's layout.
	 */
	static final EntityDataLayout<EntityType> LAYOUT = new EntityDataLayout<>(Map.ofEntries(
			Map.entry(EntityType.ITEM, ITEM_AT_8),
			Map.entry(EntityType.ITEM_FRAME, ITEM_AT_9),
			Map.entry(EntityType.GLOW_ITEM_FRAME, ITEM_AT_9),
			Map.entry(EntityType.SNOWBALL, ITEM_AT_8),
			Map.entry(EntityType.EGG, ITEM_AT_8),
			Map.entry(EntityType.ENDER_PEARL, ITEM_AT_8),
			Map.entry(EntityType.EXPERIENCE_BOTTLE, ITEM_AT_8),
			Map.entry(EntityType.SPLASH_POTION, ITEM_AT_8),
			Map.entry(EntityType.LINGERING_POTION, ITEM_AT_8),
			Map.entry(EntityType.EYE_OF_ENDER, ITEM_AT_8),
			Map.entry(EntityType.FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.SMALL_FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.FIREWORK_ROCKET, ITEM_AT_8),
			Map.entry(EntityType.OMINOUS_ITEM_SPAWNER, ITEM_AT_8),
			Map.entry(EntityType.ITEM_DISPLAY, DISPLAY_AT_23),
			Map.entry(EntityType.TEXT_DISPLAY, TEXT_AT_23)
	));
}
