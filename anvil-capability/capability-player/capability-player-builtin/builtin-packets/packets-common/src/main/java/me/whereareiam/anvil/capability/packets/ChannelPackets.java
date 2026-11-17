package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.capability.packets.model.PacketDifference;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.recording.PacketComparison;
import me.whereareiam.anvil.capability.packets.recording.RecordingFiles;
import me.whereareiam.anvil.capability.packets.tap.ChannelPacketTaps;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A player's Packets capability over its typed channel: taps and recordings open on the player's worker through
 * {@link ChannelPacketTaps}, recordings are read and written by {@link RecordingFiles} and compared by
 * {@link PacketComparison}.
 */
final class ChannelPackets implements Packets {
	private final ChannelPacketTaps taps;
	private final RecordingFiles files = new RecordingFiles();
	private final PacketComparison comparison = new PacketComparison();

	/**
	 * Creates the capability of one player.
	 *
	 * @param channel the player's channel
	 * @param player the player's name, which recordings name
	 * @param minecraft the Minecraft version of the player's client, which recordings name
	 */
	ChannelPackets(@NotNull ProtocolChannel channel, @NotNull String player, @NotNull String minecraft) {
		this.taps = new ChannelPacketTaps(channel, player, minecraft);
	}

	@Override
	public @NotNull PacketTap tap(@NotNull PacketSelection selection) {
		return taps.tap(selection);
	}

	@Override
	public @NotNull PacketRecording record() {
		return taps.record();
	}

	@Override
	public @NotNull RecordedPackets read(@NotNull Path file) throws IOException {
		return files.read(file);
	}

	@Override
	public void write(@NotNull RecordedPackets recording, @NotNull Path file) throws IOException {
		files.write(recording, file);
	}

	@Override
	public @NotNull List<PacketDifference> compare(
			@NotNull RecordedPackets expected,
			@NotNull RecordedPackets actual,
			@NotNull PacketMask mask
	) {
		return comparison.compare(expected, actual, mask);
	}
}
