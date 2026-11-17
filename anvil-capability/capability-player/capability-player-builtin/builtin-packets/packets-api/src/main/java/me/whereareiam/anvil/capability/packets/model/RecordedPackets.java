package me.whereareiam.anvil.capability.packets.model;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A recorded packet stream: every packet one player received while the recording ran, as its frame, in the order the
 * packets arrived, with what the recording was made with.
 */
@Value
public class RecordedPackets {
	/**
	 * What the recording was made with.
	 */
	@NotNull RecordingHeader header;
	/**
	 * The frames, in the order they arrived.
	 */
	@NotNull List<ClientboundFrame> frames;
}
