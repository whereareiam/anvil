package me.whereareiam.anvil.edition.java.protocol.model.entity;

import lombok.Value;

/**
 * The protocol ID the server gave an entity for one connection, which the packets about that entity name. It has
 * meaning only within that connection: the server numbers entities as it creates them, so the same entity has another
 * ID after a reconnect, and two runs of one scenario, or two builds of one server, may number the same entities
 * differently.
 *
 * <p>It is a type of its own rather than a plain number so that whatever reads packets can tell entity IDs from other
 * numbers: a comparison of two recorded packet streams pairs the IDs of one stream with the other's by the order they
 * first appear in, instead of requiring equal numbers.</p>
 *
 * <pre>{@code
 * protocol.listen(ClientboundRemoveEntities.class, removed -> {
 *     if (removed.entityIds().contains(tracked)) tracked = null;
 * });
 * }</pre>
 */
@Value
public class EntityId {
	/**
	 * The number the server sent.
	 */
	int value;
}
