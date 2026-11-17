package me.whereareiam.anvil.capability.packets.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * What a recording of a packet stream was made with: the version of its format, the protocol whose packets it holds,
 * the Minecraft version of the player's client and the player's name. Two recordings compare only when their protocols
 * are the same, since packet IDs and bytes mean something else in another protocol.
 */
@Value
public class RecordingHeader {
	/**
	 * The version of the recording format this Anvil writes and reads.
	 */
	public static final int FORMAT = 1;

	/**
	 * The version of the recording's format.
	 */
	int format;
	/**
	 * The number of the protocol the player spoke, such as {@code 774}.
	 */
	int protocol;
	/**
	 * The Minecraft version of the player's client, such as {@code 1.21.11}.
	 */
	@NotNull String minecraft;
	/**
	 * The name of the player whose packets the recording holds.
	 */
	@NotNull String player;
}
