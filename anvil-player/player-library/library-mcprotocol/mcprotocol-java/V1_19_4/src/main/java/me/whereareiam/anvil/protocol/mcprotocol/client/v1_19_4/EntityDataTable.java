package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;

import java.util.Map;

/**
 * The entity data indexes of the MCProtocolLib 1.19.4 release that carry a value the protocol models, by entity type.
 * Dropped items, item frames, thrown items, eyes of ender, fireballs and fireworks keep the item they show at 8.
 * Displays arrived in Minecraft 1.19.4 with fourteen values from index 8, so a text display keeps its text and an
 * item display its item at 22, one index before Minecraft 1.20.2 split their interpolation duration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityDataTable {
	private static final Map<Integer, EntityDataField> ITEM_AT_8 = Map.of(8, EntityDataField.SHOWN_ITEM);
	private static final Map<Integer, EntityDataField> DISPLAY_AT_22 = Map.of(22, EntityDataField.SHOWN_ITEM);
	private static final Map<Integer, EntityDataField> TEXT_AT_22 = Map.of(22, EntityDataField.DISPLAY_TEXT);

	/**
	 * The release's layout.
	 */
	static final EntityDataLayout<EntityType> LAYOUT = new EntityDataLayout<>(Map.ofEntries(
			Map.entry(EntityType.ITEM, ITEM_AT_8),
			Map.entry(EntityType.ITEM_FRAME, ITEM_AT_8),
			Map.entry(EntityType.GLOW_ITEM_FRAME, ITEM_AT_8),
			Map.entry(EntityType.SNOWBALL, ITEM_AT_8),
			Map.entry(EntityType.EGG, ITEM_AT_8),
			Map.entry(EntityType.ENDER_PEARL, ITEM_AT_8),
			Map.entry(EntityType.EXPERIENCE_BOTTLE, ITEM_AT_8),
			Map.entry(EntityType.POTION, ITEM_AT_8),
			Map.entry(EntityType.EYE_OF_ENDER, ITEM_AT_8),
			Map.entry(EntityType.FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.SMALL_FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.FIREWORK_ROCKET, ITEM_AT_8),
			Map.entry(EntityType.ITEM_DISPLAY, DISPLAY_AT_22),
			Map.entry(EntityType.TEXT_DISPLAY, TEXT_AT_22)
	));
}
