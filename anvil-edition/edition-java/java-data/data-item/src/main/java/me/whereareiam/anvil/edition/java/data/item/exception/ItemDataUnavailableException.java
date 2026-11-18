package me.whereareiam.anvil.edition.java.data.item.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that Anvil ships no item keys for a protocol: either the protocol is not one Anvil supports, or the data
 * source has no item file for it yet. The message names the protocol and the reason.
 */
@Getter
public class ItemDataUnavailableException extends IllegalStateException {
	/**
	 * The protocol number without item keys.
	 */
	private final int protocol;

	/**
	 * Creates the failure.
	 *
	 * @param protocol the protocol number without item keys
	 * @param reason why there are none, such as the data source that lacks them
	 */
	public ItemDataUnavailableException(int protocol, @NotNull String reason) {
		super("No item data for protocol " + protocol + ": " + reason);
		this.protocol = protocol;
	}
}
