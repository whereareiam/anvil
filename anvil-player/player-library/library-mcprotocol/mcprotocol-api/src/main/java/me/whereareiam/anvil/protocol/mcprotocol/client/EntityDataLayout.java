package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.mcprotocol.client.type.EntityDataField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The entity data indexes of one release that carry a value the protocol models, by entity type. Every entity of every
 * release keeps its shared flags at index 0, its custom name at 2 and whether the name is shown at 3; what other
 * indexes carry depends on the entity's type and changes between releases, which each segment lists in its table.
 *
 * <pre>{@code
 * EntityDataLayout<EntityType> layout = new EntityDataLayout<>(Map.of(
 *         EntityType.ITEM, Map.of(8, EntityDataField.SHOWN_ITEM)
 * ));
 * EntityDataField field = layout.field(EntityType.ITEM, 8);
 * }</pre>
 *
 * @param <T> the release's entity type
 */
public final class EntityDataLayout<T> {
	private static final Map<Integer, EntityDataField> EVERY_ENTITY = Map.of(
			0, EntityDataField.SHARED_FLAGS,
			2, EntityDataField.CUSTOM_NAME,
			3, EntityDataField.CUSTOM_NAME_VISIBLE
	);

	private final Map<T, Map<Integer, EntityDataField>> byType;

	/**
	 * Creates the layout of one release.
	 *
	 * @param byType the indexes beyond the ones every entity keeps, by the types that keep them
	 */
	public EntityDataLayout(@NotNull Map<T, Map<Integer, EntityDataField>> byType) {
		this.byType = Map.copyOf(byType);
	}

	/**
	 * Tells what an index carries for an entity of a type.
	 *
	 * @param type the entity's type, or {@code null} for an entity the session never added, such as the player
	 * @param index the entity data index
	 * @return the value the index carries, or {@code null} for one the protocol does not model
	 */
	public @Nullable EntityDataField field(@Nullable T type, int index) {
		EntityDataField common = EVERY_ENTITY.get(index);
		if (common != null || type == null) return common;

		return byType.getOrDefault(type, Map.of()).get(index);
	}
}
