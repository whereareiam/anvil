package me.whereareiam.anvil.protocol.java.api.model.entity.state;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.entity.AttributeOperation;
import org.jetbrains.annotations.NotNull;

/**
 * One modifier of an attribute, such as the one sprinting adds to the movement speed.
 */
@Value
public class AttributeModifier {
	/**
	 * The modifier's ID: a namespaced key, such as {@code minecraft:sprinting}, or, before Minecraft 1.21, a UUID in its
	 * text form.
	 */
	@NotNull String id;
	/**
	 * The modifier's amount.
	 */
	double amount;
	/**
	 * How the amount changes the attribute's value.
	 */
	@NotNull AttributeOperation operation;
}
