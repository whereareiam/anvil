package me.whereareiam.anvil.capability.protocol.api.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a player-scoped capability event by its identifier and immutable payload schema.
 * This descriptor is shared by publishers and subscribers; each emitted payload is a separate value.
 *
 * @param <E> payload type, or {@link Void} for an event without a payload
 */
@Value
public class EventDescriptor<E> {
	/**
	 * Namespaced identifier shared by event publishers and subscribers.
	 */
	@NotNull String id;
	/**
	 * Explicit payload schema; {@link Void} denotes an event without a value.
	 */
	@NotNull Class<E> payloadType;
}
