package me.whereareiam.anvil.protocol.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Reports that a native worker has no usable adapter for a port on its loaded library release.
 *
 * <p>{@code NativePlayer.adapter(Class)} throws it when no segment selected for the release provides the port,
 * when the providing segment failed its linkage self-check, or when more than one adapter is provided. The message
 * is the reason a capability bridge reports for the capability that asked.</p>
 */
public class NativeAdapterUnavailableException extends IllegalStateException {
	/**
	 * Creates the failure with the reason the adapter is unavailable.
	 *
	 * @param message reason, naming the port and, where known, the segment and the member that does not link
	 */
	public NativeAdapterUnavailableException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates the failure with the reason and its originating failure.
	 *
	 * @param message reason, naming the port and, where known, the segment and the member that does not link
	 * @param cause originating failure, such as an adapter that could not be created
	 */
	public NativeAdapterUnavailableException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
