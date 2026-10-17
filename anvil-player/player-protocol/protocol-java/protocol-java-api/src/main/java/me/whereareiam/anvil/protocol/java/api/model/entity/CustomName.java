package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The entity's custom name.
 */
@Value
public class CustomName implements EntityDataValue {
	/**
	 * The name as plain text, or {@code null} when the entity has none.
	 */
	@Nullable String name;
}
