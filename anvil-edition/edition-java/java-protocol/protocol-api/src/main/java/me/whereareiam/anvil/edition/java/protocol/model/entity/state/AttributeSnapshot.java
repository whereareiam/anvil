package me.whereareiam.anvil.edition.java.protocol.model.entity.state;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One attribute of an entity as the server sent it: its base value and every modifier on it, which together give the
 * value the game uses.
 */
@Value
public class AttributeSnapshot {
	/**
	 * The attribute's key in the names of Minecraft 1.21.2, such as {@code minecraft:movement_speed}; releases before
	 * name it with a {@code generic.} or {@code player.} prefix, which they report without.
	 */
	@NotNull String attribute;
	/**
	 * The attribute's base value.
	 */
	double base;
	/**
	 * The modifiers on the attribute, in the order the server sent them.
	 */
	@NotNull List<AttributeModifier> modifiers;
}
