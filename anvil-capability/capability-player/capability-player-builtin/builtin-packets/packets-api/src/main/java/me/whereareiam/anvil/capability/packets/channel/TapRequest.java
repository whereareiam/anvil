package me.whereareiam.anvil.capability.packets.channel;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A tap the host asks the player's worker to open, under an ID the host chose, which the worker's
 * {@link TappedFrame}s name and {@link PacketsChannel#UNTAP} closes. Packet classes cross as their kinds in the
 * protocol's {@code ClientboundPackets} catalog.
 */
@Value
public class TapRequest {
	/**
	 * The tap's ID, unique among the player's open taps.
	 */
	int id;
	/**
	 * Whether the tap takes every packet.
	 */
	boolean every;
	/**
	 * The kinds of the packet values the tap takes.
	 */
	@NotNull Set<String> kinds;
	/**
	 * The minecraft-data names of the packets the tap takes.
	 */
	@NotNull Set<String> names;
	/**
	 * Whether the tap needs each frame's bytes, which a recording keeps and a tap does not.
	 */
	boolean payloads;
}
