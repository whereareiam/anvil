package me.whereareiam.anvil.protocol.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Reports that what a worker extension needs from its player's protocol is not available on the player's library
 * release, so the extension cannot be installed for that player.
 *
 * <p>{@code PlayerBindingContext.protocol(Class)} throws it when the player does not speak the asked protocol, an
 * edition protocol throws it for a packet the release cannot carry, and {@code PlayerBindingContext.adapter(Class)}
 * throws it when no segment selected for the release provides a port, the providing segment failed its linkage
 * self-check against the loaded release, or more than one adapter is provided. The worker reports the capability
 * whose binding throws it as unavailable, with this exception's message as the reason, and keeps the player's other
 * capabilities.</p>
 */
public class ProtocolUnavailableException extends IllegalStateException {
	/**
	 * Creates the failure with the reason the protocol, packet or adapter is unavailable.
	 *
	 * @param message reason, naming what is missing and, where known, the segment and the member that does not link
	 */
	public ProtocolUnavailableException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates the failure with the reason and its originating failure.
	 *
	 * @param message reason, naming what is missing and, where known, the segment and the member that does not link
	 * @param cause originating failure, such as an adapter that could not be created
	 */
	public ProtocolUnavailableException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
