package me.whereareiam.anvil.edition.java.data.block.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that Anvil ships no block data for a protocol: either the protocol is not one Anvil supports, or the data
 * source has no block data for it yet. The message names the protocol and the reason.
 */
@Getter
public class BlockDataUnavailableException extends IllegalStateException {
	/**
	 * The protocol number without block data.
	 */
	private final int protocol;

	/**
	 * Creates the failure.
	 *
	 * @param protocol the protocol number without block data
	 * @param reason why there is none, such as the data source that lacks it
	 */
	public BlockDataUnavailableException(int protocol, @NotNull String reason) {
		super("No block data for protocol " + protocol + ": " + reason);
		this.protocol = protocol;
	}
}
