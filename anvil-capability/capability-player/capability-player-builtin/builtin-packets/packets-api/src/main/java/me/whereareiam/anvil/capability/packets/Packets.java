package me.whereareiam.anvil.capability.packets;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.packets.exception.RecordingMismatchException;
import me.whereareiam.anvil.capability.packets.model.PacketDifference;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Observes the packets the player's client receives, in the order they arrive on the wire. A {@link PacketTap} takes
 * the packets a selection names, for a test to match them; a {@link PacketRecording} keeps every packet with its bytes,
 * for a test to compare the whole stream with another, such as one recorded on another build of the server.
 *
 * <p>Packets cross from the player's worker only while a tap or a recording is open. A packet's name is
 * minecraft-data's, such as {@code map_chunk}; its value, when the Java protocol models it, is the protocol's
 * packet class. A {@linkplain ClientboundPackets#isCostly costly} packet, such as a chunk column, has its value only
 * in a tap that selects its class; other taps and every recording keep its bytes alone.</p>
 *
 * <pre>{@code
 * Packets packets = alice.capability(Packets.class);
 * try (PacketTap tap = packets.tap(PacketSelection.of(ClientboundBlockUpdate.class))) {
 *     console.run("setblock 0 64 0 minecraft:stone");
 *     tap.received(ClientboundBlockUpdate.class, update -> update.getChange().getBlockState() != 0);
 * }
 *
 * RecordedPackets golden = packets.read(Path.of("src/anvil/resources/chunk-repair.packets.jsonl.gz"));
 * try (PacketRecording recording = packets.record()) {
 *     console.run("ptest chunk-repair");
 *     packets.assertSame(golden, recording.stop(), PacketMask.runVarying());
 * }
 * }</pre>
 */
public interface Packets extends PlayerCapability {
	/**
	 * Opens a tap that takes the packets a selection names from now on.
	 *
	 * @param selection which packets to take
	 * @return the open tap, which the caller closes
	 * @throws IllegalArgumentException when the player's protocol has no packet of a name the selection names
	 */
	@NotNull PacketTap tap(@NotNull PacketSelection selection);

	/**
	 * Starts recording every packet the player receives from now on, with its bytes.
	 *
	 * @return the running recording, which the caller stops or closes
	 */
	@NotNull PacketRecording record();

	/**
	 * Reads a recording a test wrote with {@link #write(RecordedPackets, Path)}: gzip-compressed JSON Lines, a header
	 * line followed by one line per frame.
	 *
	 * @param file the recording's file
	 * @return the recording
	 * @throws IOException when the file cannot be read
	 * @throws IllegalArgumentException when the file is no recording of a format this Anvil reads
	 */
	@NotNull RecordedPackets read(@NotNull Path file) throws IOException;

	/**
	 * Writes a recording to a file, replacing it, as gzip-compressed JSON Lines that {@link #read(Path)} reads back.
	 *
	 * @param recording the recording
	 * @param file the file
	 * @throws IOException when the file cannot be written
	 */
	void write(@NotNull RecordedPackets recording, @NotNull Path file) throws IOException;

	/**
	 * Compares two recordings beyond what a mask leaves out. The frames pair up in the order of both streams, aligned
	 * so that a frame only one stream has is one difference; a pair differs in its state, its name, or its bytes, or,
	 * for a packet class the mask compares by value, in its value without the ignored fields. Entity IDs in values are
	 * compared by correspondence: the first ID each stream names for an entity pairs them, and every later packet must
	 * name the paired ID.
	 *
	 * @param expected the recording to compare with, such as one of a known-good build
	 * @param actual the recording compared
	 * @param mask what to leave out
	 * @return every difference, in the order of the streams, empty when they are the same
	 * @throws IllegalArgumentException when the recordings are of different protocols, or the mask names a field a
	 * packet class does not have
	 */
	@NotNull List<PacketDifference> compare(
			@NotNull RecordedPackets expected,
			@NotNull RecordedPackets actual,
			@NotNull PacketMask mask
	);

	/**
	 * Checks that two recordings are the same beyond what a mask leaves out, as {@link #compare} compares them.
	 *
	 * @param expected the recording to compare with
	 * @param actual the recording compared
	 * @param mask what to leave out
	 * @throws RecordingMismatchException when they differ
	 * @throws IllegalArgumentException when the recordings are of different protocols, or the mask names a field a
	 * packet class does not have
	 */
	default void assertSame(
			@NotNull RecordedPackets expected,
			@NotNull RecordedPackets actual,
			@NotNull PacketMask mask
	) {
		List<PacketDifference> differences = compare(expected, actual, mask);
		if (!differences.isEmpty()) throw new RecordingMismatchException(differences);
	}
}
