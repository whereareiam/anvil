package me.whereareiam.anvil.capability.packets.tap;

import me.whereareiam.anvil.capability.packets.PacketRecording;
import me.whereareiam.anvil.capability.packets.channel.TappedFrame;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * One running recording: it keeps every frame the worker sent for it, with its bytes, until it stops.
 */
final class ChannelPacketRecording implements PacketRecording {
	private final int id;
	private final RecordingHeader header;
	private final IntConsumer closer;
	private final List<ClientboundFrame> frames = new CopyOnWriteArrayList<>();
	private final AtomicBoolean stopped = new AtomicBoolean();

	ChannelPacketRecording(int id, @NotNull RecordingHeader header, @NotNull IntConsumer closer) {
		this.id = id;
		this.header = header;
		this.closer = closer;
	}

	@Override
	public @NotNull RecordedPackets stop() {
		if (!stopped.compareAndSet(false, true)) throw new IllegalStateException("The recording was already stopped");

		closer.accept(id);
		return new RecordedPackets(header, List.copyOf(frames));
	}

	@Override
	public void close() {
		if (stopped.compareAndSet(false, true)) closer.accept(id);
		frames.clear();
	}

	/**
	 * Keeps a frame the worker sent for the recording, while it runs.
	 */
	void receive(@NotNull TappedFrame frame) {
		if (stopped.get()) return;

		byte[] payload = Objects.requireNonNull(frame.getPayload(), "The worker sent a recorded frame without its bytes");
		frames.add(new ClientboundFrame(frame.getState(), frame.getId(), frame.getName(), payload, frame.getPacket()));
	}
}
