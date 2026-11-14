package me.whereareiam.anvil.capability.api.event.lifecycle;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;

/**
 * The capability's owner is closed for good: a process's capabilities when the process finally stops, a player's
 * when the player is destroyed, and every capability created so far when composing its owner fails. It is reported
 * once; an action registered for it afterwards runs at once.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Closed implements CapabilityEvent {
	/**
	 * The only value, since closing carries nothing.
	 */
	public static final Closed INSTANCE = new Closed();

	@Override
	public String toString() {
		return "Closed";
	}
}
