package me.whereareiam.anvil.edition.java.data.packet.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that Anvil ships no packet names for a protocol: either the protocol is not one Anvil supports, or the data
 * source has no protocol file for it yet. The message names the protocol and the reason.
 */
@Getter
public class PacketDataUnavailableException extends IllegalStateException {
	/**
	 * The protocol number without packet names.
	 */
	private final int protocol;

	/**
	 * Creates the failure.
	 *
	 * @param protocol the protocol number without packet names
	 * @param reason why there are none, such as the data source that lacks them
	 */
	public PacketDataUnavailableException(int protocol, @NotNull String reason) {
		super("No packet data for protocol " + protocol + ": " + reason);
		this.protocol = protocol;
	}
}
