package me.whereareiam.anvil.capability.entities;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;

/**
 * The entity read and the entity-change event shared by the host entities capability and the worker's entities
 * binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntitiesOperations {
	/**
	 * Reads the entities the player's current connection knows, with their current positions.
	 */
	public static final ChannelOperation<Void, KnownEntities> KNOWN = new ChannelOperation<>("entities.known", Void.class, KnownEntities.class);
	/**
	 * A change of an entity one of the player's connections knows, numbered by that connection.
	 */
	public static final EventDescriptor<EntityChange> CHANGED = new EventDescriptor<>("entities.changed", EntityChange.class);
}
