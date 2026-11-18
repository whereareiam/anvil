package me.whereareiam.anvil.capability.packets.model;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A recorded packet stream: every packet one player received while the recording ran, as its frame, in the order the
 * packets arrived, with what the recording was made with.
 *
 * <p>A recording keeps the value of every packet the protocol models except the
 * {@linkplain ClientboundPackets#isCostly costly} ones, such as a chunk column, whose frames it keeps as their bytes
 * alone, whether or not something else had the release decode them while it ran. So the same stream always records the
 * same way, and a recording does not carry a chunk twice.</p>
 */
@Value
public class RecordedPackets {
	/**
	 * What the recording was made with.
	 */
	@NotNull RecordingHeader header;
	/**
	 * The frames, in the order they arrived, without the values of costly packets.
	 */
	@NotNull List<ClientboundFrame> frames;

	/**
	 * Creates a recording, leaving out the values of costly packets.
	 *
	 * @param header what the recording was made with
	 * @param frames the frames, in the order they arrived
	 */
	public RecordedPackets(@NotNull RecordingHeader header, @NotNull List<ClientboundFrame> frames) {
		this.header = header;
		this.frames = frames.stream().map(RecordedPackets::recorded).toList();
	}

	private static ClientboundFrame recorded(ClientboundFrame frame) {
		ClientboundPacket packet = frame.packet().orElse(null);
		if (packet == null || !ClientboundPackets.isCostly(packet.getClass())) return frame;

		return new ClientboundFrame(frame.getState(), frame.getId(), frame.name().orElse(null), frame.payload(), null);
	}
}
