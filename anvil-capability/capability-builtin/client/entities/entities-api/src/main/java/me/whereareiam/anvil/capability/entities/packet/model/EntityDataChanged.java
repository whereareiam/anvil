package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Value;
import me.whereareiam.anvil.capability.entities.packet.model.data.EntityData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server changed data of an entity. Only the values the release's segment decodes are listed; a packet that
 * changes none of them is not reported.
 */
@Value
public class EntityDataChanged implements EntityPacket {
	int entityId;
	@NotNull List<EntityData> values;
}
