package me.whereareiam.anvil.capability.entities.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.KnownEntities;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host entities capability and its worker extensions: the capability ID both
 * register under, the read the host requests and the event the worker emits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EntitiesChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.entities";
	/**
	 * Reads the entities the player's current connection knows, with their current positions.
	 */
	public static final OperationDescriptor<Void, KnownEntities> KNOWN = new OperationDescriptor<>(
			"entities.known", Void.class, KnownEntities.class
	);
	/**
	 * A change of an entity one of the player's connections knows, numbered by that connection.
	 */
	public static final EventDescriptor<EntityChange> CHANGED = new EventDescriptor<>(
			"entities.changed", EntityChange.class
	);
}
