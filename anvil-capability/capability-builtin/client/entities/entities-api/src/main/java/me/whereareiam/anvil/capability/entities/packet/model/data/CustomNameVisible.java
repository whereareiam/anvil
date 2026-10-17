package me.whereareiam.anvil.capability.entities.packet.model.data;

import lombok.Value;

/**
 * Whether the client shows the entity's custom name.
 */
@Value
public class CustomNameVisible implements EntityData {
	boolean visible;
}
