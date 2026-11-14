package me.whereareiam.anvil.capability.api.event;

/**
 * A moment in the life of a capability's owner that the capability can act on through
 * {@code CapabilityContext.on(Class, Consumer)}. Each type carries what is known at its moment, and its
 * documentation says which owners report it and when.
 *
 * <p>The moments are grouped by subject: {@code event.scenario} for a scenario using the owner, and
 * {@code event.lifecycle} for the owner's own life, such as a restart or its closing. A new moment becomes a new type here and the point in composition that reports it, never a
 * separate registration method or action list. An owner refuses an action for a moment it never reports.</p>
 */
public interface CapabilityEvent {
}
