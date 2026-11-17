package me.whereareiam.anvil.capability.packets.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One way two recorded packet streams differ, at the positions of the frames it concerns, which count every frame of
 * each stream from {@code 0}, those a mask ignored included. A frame only one stream has names {@code -1} as the other
 * stream's position.
 */
@Value
public class PacketDifference {
	/**
	 * The position of the frame in the expected stream, or {@code -1} when only the actual stream has it.
	 */
	int expected;
	/**
	 * The position of the frame in the actual stream, or {@code -1} when only the expected stream has it.
	 */
	int actual;
	/**
	 * What differs, such as a missing packet, another packet, a field or the bytes from an offset on.
	 */
	@NotNull String description;
}
