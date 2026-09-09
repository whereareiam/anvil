package me.whereareiam.anvil.api.capability;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only access to the capabilities belonging to one owner.
 * Implementations own discovery and lifetime; this contract exposes typed lookup only.
 *
 * @param <C> capability scope accepted by this owner
 */
public interface CapabilityOwner<C extends Capability> {
	/**
	 * Resolves an installed capability by its exact public interface.
	 *
	 * @param type public capability interface
	 * @param <T> requested capability type
	 * @return owner-scoped capability
	 * @throws CapabilityUnavailableException when the capability is unavailable
	 */
	@NotNull <T extends C> T capability(@NotNull Class<T> type);

	/**
	 * Reports whether this owner supplies the requested capability.
	 *
	 * @param type public capability interface
	 * @return whether the capability is available
	 */
	boolean hasCapability(@NotNull Class<? extends C> type);
}
