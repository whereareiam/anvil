package me.whereareiam.anvil.protocol.java.api.model.entity;

/**
 * One entity data value the protocol models, which the player's release decoded from the index the entity's type
 * keeps it at. Values the protocol does not model are left out.
 *
 * <pre>{@code
 * switch (value) {
 *     case SharedFlags flags -> invisible = flags.isInvisible();
 *     case CustomName name -> customName = name.getName();
 *     // ...
 * }
 * }</pre>
 */
public sealed interface EntityDataValue permits SharedFlags, CustomName, CustomNameVisible, DisplayText, ShownItem {
}
