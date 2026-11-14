package me.whereareiam.anvil.capability.api.event.lifecycle;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;

/**
 * A later generation of the capability's process is ready: the process was restarted and its agent connected
 * again. The capability survives the restart, but whatever the previous generation's JVM held is gone, so an action
 * can bring the new generation up to what the capability expects.
 *
 * <p>A failed action fails the restart.</p>
 *
 * <p>Reported to process capabilities only, never for the first generation, which creates the capability.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProcessRestarted implements CapabilityEvent {
	/**
	 * The only value, since a restart carries nothing a capability does not already know.
	 */
	public static final ProcessRestarted INSTANCE = new ProcessRestarted();

	@Override
	public String toString() {
		return "ProcessRestarted";
	}
}
