package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import org.jetbrains.annotations.NotNull;

/**
 * A running recording of every packet the player receives, with its bytes, which {@link Packets#record()} starts.
 * Recordings of a deterministic scenario on two builds of a server compare through
 * {@link Packets#compare(RecordedPackets, RecordedPackets, PacketMask)}.
 *
 * <pre>{@code
 * try (PacketRecording recording = packets.record()) {
 *     console.run("ptest chunk-repair");
 *     packets.write(recording.stop(), Path.of("build/chunk-repair.packets.jsonl.gz"));
 * }
 * }</pre>
 */
public interface PacketRecording extends AutoCloseable {
	/**
	 * Stops recording and returns what was recorded.
	 *
	 * @return the recorded packets, in the order they arrived
	 * @throws IllegalStateException when the recording was already stopped or closed
	 */
	@NotNull RecordedPackets stop();

	/**
	 * Stops recording, if it still runs, and forgets what was recorded.
	 */
	@Override
	void close();
}
