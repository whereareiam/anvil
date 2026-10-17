package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.entity.SharedFlags;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The entity data indexes of Minecraft 1.21.1 that carry a value the protocol models, by entity type. Every entity
 * keeps its shared flags at index 0, its custom name at 2 and whether the name is shown at 3. Dropped items, item
 * frames, thrown items, eyes of ender, fireballs, fireworks and ominous item spawners keep the item they show at 8.
 * A display keeps fifteen values from index 8, so a text display keeps its text and an item display its item at 23.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityDataTable {
	private static final int ON_FIRE = 0x01;
	private static final int SNEAKING = 0x02;
	private static final int SPRINTING = 0x08;
	private static final int SWIMMING = 0x10;
	private static final int INVISIBLE = 0x20;
	private static final int GLOWING = 0x40;
	private static final int GLIDING = 0x80;

	private static final Map<Integer, Field> EVERY_ENTITY = Map.of(
			0, Field.SHARED_FLAGS,
			2, Field.CUSTOM_NAME,
			3, Field.CUSTOM_NAME_VISIBLE
	);
	private static final Map<Integer, Field> ITEM_AT_8 = Map.of(8, Field.SHOWN_ITEM);
	private static final Map<EntityType, Map<Integer, Field>> BY_TYPE = Map.ofEntries(
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
			Map.entry(EntityType.OMINOUS_ITEM_SPAWNER, ITEM_AT_8),
			Map.entry(EntityType.ITEM_DISPLAY, Map.of(23, Field.SHOWN_ITEM)),
			Map.entry(EntityType.TEXT_DISPLAY, Map.of(23, Field.DISPLAY_TEXT))
	);

	/**
	 * Tells what an index carries for an entity of a type.
	 *
	 * @param type the entity's type, or {@code null} for an entity the session never added, such as the player
	 * @param index the entity data index
	 * @return the value the index carries, or {@code null} for one the protocol does not model
	 */
	static @Nullable Field field(@Nullable EntityType type, int index) {
		Field common = EVERY_ENTITY.get(index);
		if (common != null || type == null) return common;

		return BY_TYPE.getOrDefault(type, Map.of()).get(index);
	}

	/**
	 * Reads the shared flags byte.
	 *
	 * @param flags the byte the server sent
	 * @return the flags it sets
	 */
	static SharedFlags sharedFlags(byte flags) {
		return SharedFlags.builder()
				.onFire((flags & ON_FIRE) != 0)
				.sneaking((flags & SNEAKING) != 0)
				.sprinting((flags & SPRINTING) != 0)
				.swimming((flags & SWIMMING) != 0)
				.invisible((flags & INVISIBLE) != 0)
				.glowing((flags & GLOWING) != 0)
				.gliding((flags & GLIDING) != 0)
				.build();
	}

	/**
	 * A value the protocol models, which an index carries.
	 */
	enum Field {
		SHARED_FLAGS,
		CUSTOM_NAME,
		CUSTOM_NAME_VISIBLE,
		SHOWN_ITEM,
		DISPLAY_TEXT
	}
}
