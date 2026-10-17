package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.EntityDataLayout;
import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;

import java.util.Map;

/**
 * The entity data indexes of the MCProtocolLib 1.18.2 release that carry a value the protocol models, by entity type.
 * Dropped items, item frames, thrown items, eyes of ender, fireballs and fireworks keep the item they show at 8. The
 * release has no displays, which arrived in Minecraft 1.19.4.
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
			Map.entry(EntityType.EGG, ITEM_AT_8),
			Map.entry(EntityType.ENDER_PEARL, ITEM_AT_8),
			Map.entry(EntityType.EXPERIENCE_BOTTLE, ITEM_AT_8),
			Map.entry(EntityType.POTION, ITEM_AT_8),
			Map.entry(EntityType.EYE_OF_ENDER, ITEM_AT_8),
			Map.entry(EntityType.FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.SMALL_FIREBALL, ITEM_AT_8),
			Map.entry(EntityType.FIREWORK_ROCKET, ITEM_AT_8)
	));
}
