package me.whereareiam.anvil.capability.entities.model;

import lombok.Value;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One change of an entity the player sees, with the entity before and after it.
 */
@Value
public class EntityChange {
	/**
	 * What the change did.
	 */
	@NotNull EntityChangeKind kind;
	/**
	 * The entity after the change; for a disappearance, as it was last known.
	 */
	@NotNull SeenEntity entity;
	/**
	 * The entity before a {@link EntityChangeKind#CHANGED} change, or null for an appearance or a disappearance.
	 */
	@Nullable SeenEntity previous;
}
