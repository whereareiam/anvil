package me.whereareiam.anvil.capability.protocol.api.exception;

import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that a player's worker has no usable adapter for a port, so the capability asking for it cannot be
 * installed on that player's library release.
 *
 * <p>{@code PlayerBindingContext.adapter(Class)} throws it when no segment selected for the release provides the
 * port, when the providing segment failed its linkage self-check against the loaded release, or when more than one
 * adapter is provided. The worker reports the capability whose binding throws it as unavailable, with this
 * exception's message as the reason, and keeps the player's other capabilities.</p>
 */
public class AdapterUnavailableException extends CapabilityException {
	/**
	 * Creates the failure with the reason the adapter is unavailable.
	 *
	 * @param message reason, naming the port and, where known, the segment and the member that does not link
	 */
	public AdapterUnavailableException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates the failure with the reason and its originating failure.
	 *
	 * @param message reason, naming the port and, where known, the segment and the member that does not link
	 * @param cause originating failure, such as the native worker's own report
	 */
	public AdapterUnavailableException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
