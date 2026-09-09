package me.whereareiam.anvil.capability.api;

import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Dependencies and cleanup available while creating a capability for one owner.
 * A provider can retrieve only capabilities declared in its descriptor.
 *
 * @param <C> capabilities belonging to the owner
 */
public interface CapabilityContext<C extends Capability> {
	/**
	 * Finds a previously created, explicitly declared dependency on this owner.
	 *
	 * @param type required capability type
	 * @param <T> required capability type
	 * @return the dependency, or empty when unavailable or undeclared
	 */
	@NotNull <T extends C> Optional<T> findCapability(@NotNull Class<T> type);

	/**
	 * Resolves an explicitly declared dependency on this owner.
	 *
	 * @param type required capability type
	 * @param <T> required capability type
	 * @return the dependency
	 * @throws CapabilityException when the capability is unavailable or undeclared
	 */
	default @NotNull <T extends C> T requireCapability(@NotNull Class<T> type) {
		return findCapability(type).orElseThrow(() -> new CapabilityException(
				"Required capability is unavailable or undeclared: " + type.getName()
		));
	}

	/**
	 * Registers cleanup for the owning capability set. Actions run in reverse registration
	 * order, including when creation fails after registration. Registration does not transfer
	 * ownership of other services borrowed through an owner-specific context. An action
	 * registered after closure is executed immediately.
	 *
	 * @param action cleanup action
	 */
	void onClose(@NotNull Runnable action);
}
