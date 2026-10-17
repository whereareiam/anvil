package me.whereareiam.anvil.capability.entities.packet.model.data;

/**
 * One entity data value a segment decoded from an entity data packet.
 */
public sealed interface EntityData permits SharedFlags, CustomName, CustomNameVisible, DisplayText, ShownItem {
}
